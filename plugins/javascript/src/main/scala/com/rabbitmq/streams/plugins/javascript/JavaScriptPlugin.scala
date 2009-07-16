/*
 * A plugin that evaluates a supplied JavaScript function to transform messages.
 */

package com.rabbitmq.streams.plugins.javascript

import com.rabbitmq.streams.harness.{PipelineComponent, InputReader, Publisher}
import net.sf.json.JSONObject

import org.mozilla.javascript.Context

class JavaScriptPlugin(config : JSONObject) extends PipelineComponent(config) {

    private val context = Context.enter()

    private val globalScope = context.initStandardObjects(null, false)
    // TODO Add things into the scope, then seal it.
    // TODO security object
    private val functionText = cleanUpFunctionValue(config.getJSONObject("configuration").getString("function"))

    private val function = context.compileFunction(globalScope, functionText, "<config>", 1, null)

    protected def cleanUpFunctionValue(func : String) : String =
        if (func.startsWith("\"") && func.endsWith("\""))
            func.substring(1, func.length-1)
        else
            func



    object input extends InputReader {
        override def handleBody(body : Array[Byte]) {
            val strValue = new String(body)
            val context = Context.enter()
            val result = function.call(context, globalScope, function, Array(strValue))
            getPublisher("output").publish(result.toString.getBytes)
        }
    }
}
