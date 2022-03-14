package klite.jobs

import klite.*
import klite.jdbc.Transaction
import klite.jdbc.TransactionContext
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineStart.UNDISPATCHED
import java.time.Duration.between
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.TimeUnit.SECONDS
import java.util.concurrent.atomic.AtomicLong
import javax.sql.DataSource

fun interface Job {
  suspend fun run()
}

class JobRunner(
  private val db: DataSource,
  private val executor: ScheduledExecutorService = Executors.newScheduledThreadPool(Config.optional("JOB_RUNNER_THREADS", "3").toInt())
): Extension, CoroutineScope {
  override val coroutineContext = executor.asCoroutineDispatcher()
  private val logger = logger()
  private val seq = AtomicLong()
  private val runningJobs = ConcurrentHashMap.newKeySet<kotlinx.coroutines.Job>()

  override fun install(server: Server) {
    server.onStop(::gracefulStop)
  }

  internal fun runInTransaction(jobName: String, job: Job) {
    val threadName = RequestThreadNameContext("${RequestLogger.prefix}/$jobName#${seq.incrementAndGet()}")
    val tx = Transaction(db)
    val launched = launch(start = UNDISPATCHED, context = TransactionContext(tx) + threadName) {
      try {
        job.run()
        tx.close(true)
      } catch (e: Exception) {
        logger.error("$jobName failed", e)
        tx.close(false)
      }
    }
    runningJobs += launched
    launched.invokeOnCompletion { runningJobs -= launched }
  }

  fun schedule(job: Job, delay: Long, period: Long, unit: TimeUnit) {
    val jobName = job::class.simpleName!!
    val startAt = LocalDateTime.now().plus(delay, unit.toChronoUnit())
    logger.info("$jobName will start at $startAt and run every $period $unit")
    executor.scheduleAtFixedRate({ runInTransaction(jobName, job) }, delay, period, unit)
  }

  fun scheduleDaily(job: Job, delayMinutes: Long = (Math.random() * 10).toLong()) =
    schedule(job, delayMinutes, 24 * 60, MINUTES)

  fun scheduleDaily(job: Job, at: LocalTime) {
    val now = LocalDateTime.now()
    val todayAt = at.atDate(now.toLocalDate())
    val runAt = if (todayAt.isAfter(now)) todayAt else todayAt.plusDays(1)
    scheduleDaily(job, between(now, runAt).toMinutes())
  }

  fun scheduleMonthly(job: Job, dayOfMonth: Int, at: LocalTime) {
    scheduleDaily({
      if (LocalDate.now().dayOfMonth == dayOfMonth) job.run()
    }, at)
  }

  private fun gracefulStop() {
    runBlocking {
      runningJobs.forEach { it.cancelAndJoin() }
    }
    executor.shutdown()
    executor.awaitTermination(10, SECONDS)
  }
}
