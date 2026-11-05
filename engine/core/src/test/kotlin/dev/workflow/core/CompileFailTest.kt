package dev.workflow.core

import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.K2JVMCompiler
import org.jetbrains.kotlin.config.Services
import java.nio.file.Files
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.test.fail

/**
 * The promise of the DSL: the Kotlin compiler refuses a workflow whose outcomes aren't all routed.
 * Each case compiles a snippet against the real DSL on the test classpath.
 */
class CompileFailTest {

    private val header = """
        import dev.workflow.core.*
        enum class Choice { APPROVE, REJECT, COUNTER }
        enum class Other { YES, NO }
        class OtherHandler : ServiceHandler<Other> {
            override fun execute(ctx: ServiceContext) = ServiceResult(Other.YES)
        }
    """.trimIndent()

    private fun compile(body: String): List<String> {
        val dir = Files.createTempDirectory("dsl-compile")
        val src = dir.resolve("Snippet.kt").apply { writeText("$header\n$body\n") }
        val errors = mutableListOf<String>()
        val collector = object : MessageCollector {
            override fun clear() = Unit
            override fun hasErrors() = errors.isNotEmpty()
            override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
                if (severity.isError) errors += message
            }
        }
        val args = K2JVMCompilerArguments().apply {
            freeArgs = listOf(src.toString())
            classpath = System.getProperty("java.class.path")
            destination = dir.resolve("out").toString()
            jvmTarget = "25"
            noStdlib = true
            noReflect = true
            suppressWarnings = true
        }
        K2JVMCompiler().exec(collector, Services.EMPTY, args)
        return errors
    }

    private fun assertCompiles(body: String) {
        val errors = compile(body)
        if (errors.isNotEmpty()) fail("expected to compile, got:\n${errors.joinToString("\n")}")
    }

    private fun assertRejected(body: String, vararg expected: String) {
        val errors = compile(body)
        assertTrue(errors.isNotEmpty(), "expected a compile error, but it compiled")
        val all = errors.joinToString("\n")
        expected.forEach { assertTrue(it in all, "expected '$it' in:\n$all") }
    }

    @Test
    fun `E2 a fully routed workflow compiles`() = assertCompiles(
        """
        object Ok : Workflow("ok", 1, {
            userTask("review", Choice::class) {
                assignTo(role("legal"))
                on { when (it) { Choice.APPROVE -> next(); Choice.REJECT -> end("rejected"); Choice.COUNTER -> goto("review") } }
            }
        })
        """,
    )

    @Test
    fun `E2 a missing outcome does not compile`() = assertRejected(
        """
        object Missing : Workflow("missing", 1, {
            userTask("review", Choice::class) {
                assignTo(role("legal"))
                on { when (it) { Choice.APPROVE -> next(); Choice.REJECT -> end("rejected") } }
            }
        })
        """,
        "COUNTER",
    )

    @Test
    fun `E2 forgetting on does not compile`() = assertRejected(
        """
        object NoRoutes : Workflow("no-routes", 1, {
            userTask("review", Choice::class) {
                assignTo(role("legal"))
            }
        })
        """,
        "Routed",
    )

    @Test
    fun `E2 a handler returning the wrong outcome type does not compile`() = assertRejected(
        """
        object WrongHandler : Workflow("wrong", 1, {
            serviceTask("call", Choice::class) {
                handler<OtherHandler>()
                on { when (it) { Choice.APPROVE -> next(); Choice.REJECT -> next(); Choice.COUNTER -> next() } }
            }
        })
        """,
        "OtherHandler",
    )

    @Test
    fun `E2 a timed-out route needs the timeout outcome type`() = assertRejected(
        """
        object WrongWait : Workflow("wrong-wait", 1, {
            eventWait("w", Choice::class) {
                on { when (it) { Choice.APPROVE -> next(); Choice.REJECT -> next(); Choice.COUNTER -> next() } }
            }
        })
        """,
        "KClass<ReceivedOrTimedOut>",
    )

    @Test
    fun `E2 outer builders are not reachable from inside a path`() = assertRejected(
        """
        object Leak : Workflow("leak", 1, {
            userTask("review", Other::class) {
                assignTo(role("legal"))
                on { when (it) {
                    Other.YES -> path("p") { userTask("inner") { assignTo(user("x")) }; form { text("oops") } }
                    Other.NO -> next()
                } }
            }
        })
        """,
        "implicit receiver",
    )
}
