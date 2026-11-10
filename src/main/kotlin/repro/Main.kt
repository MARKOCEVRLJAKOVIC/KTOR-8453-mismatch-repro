package repro

import io.ktor.server.engine.embeddedServer
import io.ktor.server.jetty.jakarta.Jetty
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import java.io.PrintWriter
import java.lang.reflect.Proxy
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import kotlin.system.exitProcess

// Servlet and Jetty classes are used via reflection on purpose, so the project
// compiles regardless of the scope they have in Ktor's POM and adds no direct dependency on them.

fun main() {
    printServletApiInfo()

    var ok = runCase("Ktor embeddedServer(Jetty) handles a request") { ktorJetty() }

    if (classExists("org.eclipse.jetty.ee10.servlet.ServletContextHandler")) {
        ok = runCase("Jetty EE10 ServletContextHandler handles a request") { jettyEe10Servlet() } && ok
    } else {
        println("[SKIP] jetty-ee10-servlet is not on the classpath")
    }

    println(if (ok) "RESULT: OK" else "RESULT: FAILED")
    exitProcess(if (ok) 0 else 1)
}

private fun printServletApiInfo() {
    val location = runCatching {
        Class.forName("jakarta.servlet.Servlet").protectionDomain.codeSource?.location
    }.getOrElse { "not on the classpath ($it)" }
    println("Servlet API jar: $location")
    println("Servlet 6 class jakarta.servlet.ServletConnection present: ${classExists("jakarta.servlet.ServletConnection")}")
}

private fun runCase(name: String, block: () -> Unit): Boolean = try {
    block()
    println("[OK]   $name")
    true
} catch (t: Throwable) {
    println("[FAIL] $name: $t")
    t.printStackTrace(System.out)
    false
}

private fun ktorJetty() {
    val server = embeddedServer(Jetty, port = 18080) {
        routing { get("/") { call.respondText("ok") } }
    }
    server.start(wait = false)
    try {
        expectOk("http://127.0.0.1:18080/")
    } finally {
        server.stop(500, 1000)
    }
}

private fun jettyEe10Servlet() {
    val loader = Thread.currentThread().contextClassLoader
    val servletInterface = Class.forName("jakarta.servlet.Servlet")
    val responseInterface = Class.forName("jakarta.servlet.ServletResponse")
    val contextHandlerClass = Class.forName("org.eclipse.jetty.ee10.servlet.ServletContextHandler")
    val holderClass = Class.forName("org.eclipse.jetty.ee10.servlet.ServletHolder")
    val serverClass = Class.forName("org.eclipse.jetty.server.Server")
    val handlerClass = Class.forName("org.eclipse.jetty.server.Handler")

    var config: Any? = null
    val servlet = Proxy.newProxyInstance(loader, arrayOf(servletInterface)) { proxy, method, args ->
        when (method.name) {
            "init" -> { config = args[0]; null }
            "getServletConfig" -> config
            "getServletInfo" -> "repro"
            "service" -> {
                val writer = responseInterface.getMethod("getWriter").invoke(args[1]) as PrintWriter
                writer.write("ok")
                null
            }
            "destroy" -> null
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args[0]
            "toString" -> "ReproServlet"
            else -> null
        }
    }

    val context = contextHandlerClass.getConstructor().newInstance()
    val holder = holderClass.getConstructor(servletInterface).newInstance(servlet)
    contextHandlerClass.getMethod("addServlet", holderClass, String::class.java).invoke(context, holder, "/*")

    val server = serverClass.getConstructor(Int::class.javaPrimitiveType).newInstance(18081)
    serverClass.getMethod("setHandler", handlerClass).invoke(server, context)
    serverClass.getMethod("start").invoke(server)
    try {
        expectOk("http://127.0.0.1:18081/")
    } finally {
        serverClass.getMethod("stop").invoke(server)
    }
}

private fun expectOk(url: String) {
    val client = HttpClient.newBuilder().proxy(HttpClient.Builder.NO_PROXY).build()
    val response = client.send(HttpRequest.newBuilder(URI(url)).build(), HttpResponse.BodyHandlers.ofString())
    check(response.statusCode() == 200 && response.body() == "ok") {
        "Unexpected response: ${response.statusCode()} '${response.body()}'"
    }
}

private fun classExists(name: String): Boolean = runCatching { Class.forName(name) }.isSuccess
