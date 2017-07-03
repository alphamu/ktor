package org.jetbrains.ktor.sessions

import org.jetbrains.ktor.application.*
import org.jetbrains.ktor.pipeline.*
import kotlin.reflect.*

class SessionTrackerCookieValue(val type: KClass<*>, val name: String, val serializer: SessionSerializer) : SessionTracker {
    suspend override fun lookup(context: PipelineContext<Unit>, cookieSettings: SessionCookiesSettings): Any? {
        val cookie = context.call.request.cookies[name]
        val value = cookieSettings.fromCookie(cookie)
        return value?.let { serializer.deserialize(it) }
    }

    override suspend fun assign(call: ApplicationCall, session: Any, cookieSettings: SessionCookiesSettings) {
        val serialized = serializer.serialize(session)
        val cookie = cookieSettings.toCookie(name, serialized)
        call.response.cookies.append(cookie)
    }

    override suspend fun unassign(call: ApplicationCall) {
        call.response.cookies.appendExpired(name)
    }

    override fun validate(value: Any) {
        if (!type.javaObjectType.isAssignableFrom(value.javaClass)) {
            throw IllegalArgumentException("Value for this session tracker expected to be of type $type but was $value")
        }
    }
}

class CookieValueSessionTrackerBuilder<S : Any>(val type: KClass<S>) {
    var cookieName: String = "SESSION"
    var serializer: SessionSerializer = autoSerializerOf(type)

    fun build(): SessionTracker = SessionTrackerCookieValue(type, cookieName, serializer)
}

fun <S : Any> Sessions.Configuration.cookieByValue(sessionType: KClass<S>, block: CookieValueSessionTrackerBuilder<S>.() -> Unit = {}) {
    tracker = CookieValueSessionTrackerBuilder(sessionType).apply(block).build()
}
