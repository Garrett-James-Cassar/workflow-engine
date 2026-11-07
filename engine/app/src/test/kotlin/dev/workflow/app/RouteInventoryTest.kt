package dev.workflow.app

import dev.workflow.app.observability.Job
import dev.workflow.app.security.Authenticated
import dev.workflow.app.security.Permissions
import dev.workflow.app.security.Public
import dev.workflow.app.security.Requires
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.ApplicationContext
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Endpoint labels (OBSERVABILITY.md): every route and every background job has a label and a permission or action. */
class RouteInventoryTest : ApiTest() {
    @Autowired @Qualifier("requestMappingHandlerMapping") lateinit var mapping: RequestMappingHandlerMapping
    @Autowired lateinit var context: ApplicationContext

    private val sentenceCase = Regex("^[A-Z][a-z]+( [a-z0-9]+)*$")

    private val appHandlers get() = mapping.handlerMethods.values.filter { it.beanType.name.startsWith("dev.workflow.app") }

    @Test
    fun `every route declares a permission and a verb-first sentence-case label`() {
        val labels = mutableListOf<String>()
        appHandlers.forEach { h ->
            val where = "${h.beanType.simpleName}.${h.method.name}"
            val requires = h.method.getAnnotation(Requires::class.java)
            val authenticated = h.method.getAnnotation(Authenticated::class.java)
            val public = h.method.getAnnotation(Public::class.java)
            assertEquals(1, listOfNotNull(requires, authenticated, public).size, "$where declares exactly one of Requires, Authenticated, Public")
            requires?.let {
                assertTrue(it.permission in Permissions.ALL, "$where: unknown permission ${it.permission}")
                labels += it.label.also { l -> assertTrue(sentenceCase.matches(l), "$where: '$l' is not a sentence-case label") }
            }
            authenticated?.let {
                assertTrue(Regex("^[a-z-]+\\.[a-z-]+$").matches(it.action), "$where: '${it.action}' is not resource.verb")
                labels += it.label.also { l -> assertTrue(sentenceCase.matches(l), "$where: '$l' is not a sentence-case label") }
            }
        }
        assertEquals(labels.size, labels.toSet().size, "labels are unique: $labels")
        assertTrue(labels.size >= 18, "the labelled routes: ${labels.size}")
    }

    @Test
    fun `every scheduled job declares a label and an action`() {
        val jobs = context.beanDefinitionNames.mapNotNull { context.getType(it) }.filter { it.name.startsWith("dev.workflow.app") }
            .flatMap { type -> org.springframework.util.ClassUtils.getUserClass(type).methods.filter { it.isAnnotationPresent(Scheduled::class.java) } }
        assertTrue(jobs.isNotEmpty(), "the timeout poller is a scheduled job")
        jobs.forEach { m ->
            val job = m.getAnnotation(Job::class.java)
            assertTrue(job != null && sentenceCase.matches(job.label) && Regex("^[a-z-]+\\.[a-z-]+$").matches(job.action), "${m.declaringClass.simpleName}.${m.name} needs @Job(label, action)")
        }
    }
}
