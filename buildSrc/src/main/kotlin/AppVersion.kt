import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Properties
import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.api.tasks.TaskProvider

/** The date part of a version's name, as a release's (YY.MM.DD). */
private val DATE = DateTimeFormatter.ofPattern("yy.MM.dd")

/**
 * The version a build is given when it isn't passed one (-PversionName, -PgplayCode: a release's from its tag,
 * build-app.yml; the tests' from their commit, tests.yml): `<YY.MM.DD>-b<n>`, the day's n-th build on this machine,
 * version code 1. The day's count is kept in the project's .gradle (outside the build folders, so a clean doesn't
 * reset it); the task runs on every build that packages the app, so each gets the next one.
 */
abstract class DailyVersionName : DefaultTask() {
    /** The day's count: the date and its builds so far. */
    @get:Internal
    abstract val counter: RegularFileProperty

    /** The version's name, read by the app's variants. */
    @get:OutputFile
    abstract val versionName: RegularFileProperty

    init {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    fun stamp() {
        val file = counter.get().asFile
        val today = LocalDate.now().format(DATE)
        val saved = Properties().apply { if (file.exists()) file.reader().use(::load) }
        val build = if (saved.getProperty("date") == today) (saved.getProperty("build")?.toIntOrNull() ?: 0) + 1 else 1
        file.parentFile.mkdirs()
        Properties().apply {
            setProperty("date", today)
            setProperty("build", build.toString())
        }.let { props -> file.writer().use { props.store(it, "The day's builds (DailyVersionName, buildSrc's AppVersion.kt)") } }
        versionName.get().asFile.apply { parentFile.mkdirs() }.writeText("$today-b$build")
    }
}

/** The daily version's task, its name read from [DailyVersionName.versionName] as [dailyVersionName] does. */
@Suppress("unused") // app/build.gradle.kts
fun Project.registerDailyVersionName(): TaskProvider<DailyVersionName> =
    tasks.register("dailyVersionName", DailyVersionName::class.java) {
        group = "build"
        description = "Names the build <YY.MM.DD>-b<n>, the day's n-th build (when no -PversionName is passed)."
        counter.set(rootProject.layout.projectDirectory.file(".gradle/dailyBuild.properties"))
        versionName.set(layout.buildDirectory.file("version/versionName.txt"))
    }

/** The name [task] gives the build, once it's run. */
fun dailyVersionName(task: TaskProvider<DailyVersionName>): Provider<String> =
    task.flatMap { it.versionName }.map { it.asFile.readText().trim() }
