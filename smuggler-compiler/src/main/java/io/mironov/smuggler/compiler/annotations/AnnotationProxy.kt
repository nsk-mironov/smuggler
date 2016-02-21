package io.mironov.smuggler.compiler.annotations

import com.google.common.reflect.AbstractInvocationHandler
import io.mironov.smuggler.compiler.reflect.AnnotationSpec
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.util.ArrayList
import java.util.HashMap

internal object AnnotationProxy {
  fun <A> create(clazz: Class<A>, spec: AnnotationSpec): A {
    return clazz.cast(Proxy.newProxyInstance(clazz.classLoader, arrayOf(clazz), object : AbstractInvocationHandler() {
      private val cache = HashMap<String, Any?>()

      private val string by lazy(LazyThreadSafetyMode.NONE) {
        cache.map { "${it.key}=${it.value}" }.joinToString(", ")
      }

      init {
        for ((key, value) in spec.values) {
          if (value != null) {
            try {
              cache.put(key, AnnotationProxy.resolve(clazz.getMethod(key).returnType, value))
            } catch (exception: NoSuchMethodException) {
              // just ignore
            }
          }
        }
      }

      override fun handleInvocation(proxy: Any, method: Method, args: Array<out Any>): Any? {
        return cache[method.name]
      }

      override fun toString(): String {
        return "@${clazz.canonicalName}($string)"
      }
    }))
  }

  private fun resolve(type: Class<*>, value: Any): Any {
    return when {
      AnnotationProxy.isArray(type) -> AnnotationProxy.resolveArray(type.componentType, value)
      AnnotationProxy.isAnnotation(type) -> AnnotationProxy.resolveAnnotation(type, value)
      else -> AnnotationProxy.resolveValue(type, value)
    }
  }

  private fun resolveArray(type: Class<*>, value: Any): Any {
    val array = java.lang.reflect.Array.newInstance(type, 0).cast<Array<Any?>>()

    val list = value.cast<Array<Any>>().orEmpty().map {
      AnnotationProxy.resolve(type, it)
    }

    return ArrayList(list).toArray(array)
  }

  private fun resolveAnnotation(type: Class<*>, value: Any): Any {
    return AnnotationProxy.create(type, value.cast())
  }

  private fun resolveValue(type: Class<*>, value: Any): Any {
    return value
  }

  private fun isArray(type: Class<*>): Boolean {
    return type.isArray && !type.componentType.isPrimitive
  }

  private fun isAnnotation(type: Class<*>): Boolean {
    return type.isAnnotation || type.getAnnotation(AnnotationDelegate::class.java) != null
  }

  private inline fun <reified T : Any> Any?.cast(): T {
    return this as T
  }
}