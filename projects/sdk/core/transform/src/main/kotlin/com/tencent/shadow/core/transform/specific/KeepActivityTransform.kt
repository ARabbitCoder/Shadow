package com.tencent.shadow.core.transform.specific

import com.google.common.base.Joiner
import com.tencent.shadow.core.transform.JavassistUtil
import com.tencent.shadow.core.transform_kit.SpecificTransform
import com.tencent.shadow.core.transform_kit.TransformStep
import javassist.*
import javassist.bytecode.Descriptor
import javassist.bytecode.MethodInfo
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import java.io.IOException

class KeepActivityTransform(private val emptyClass: Array<String>) : SpecificTransform() {

    val fakeClass =  hashSetOf<String?>()

    override fun setup(allInputClass: Set<CtClass>) {

        JavassistUtil.parseRulesAndMakeClass(emptyClass.toList(),mClassPool)

        newStep(object : TransformStep {
            override fun filter(allInputClass: Set<CtClass>): Set<CtClass> {
                return allInputClass
            }

            override fun transform(ctClass: CtClass) {
                ctClass.declaredMethods.forEach { ctMethod ->
                    renameMethod(ctClass, ctMethod, mClassPool);
                }
            }
        })
    }

    @Throws(NotFoundException::class, CannotCompileException::class, IOException::class)
    fun renameMethod(ctClass: CtClass, ctMethod: CtMethod, classPool: ClassPool) {
        println("Javassit: ${ctClass.name}")
        ctMethod.instrument(object : ExprEditor() {
            @Throws(CannotCompileException::class)
            override fun doit(clazz: CtClass, minfo: MethodInfo): Boolean {
                return super.doit(clazz, minfo)
            }

            @Throws(CannotCompileException::class)
            override fun edit(methodCall: MethodCall) {
                super.edit(methodCall)
                val methodName = methodCall.methodName
                val className = methodCall.className
                val signature = methodCall.signature

                var returnType: CtClass?
                try {
                    returnType = Descriptor.getReturnType(signature, classPool)
                } catch (e: NotFoundException) {
                    returnType = mClassPool.makeClass(e.message)
                    fakeClass.add(e.message)
                }

                val parameterTypes = Descriptor.getParameterTypes(signature, classPool)

                if (parameterTypes != null) {

                    val paramArray = arrayOfNulls<String>(parameterTypes.size)
                    for (i in parameterTypes.indices) {
                        paramArray[i] = "$${i + 1}"
                    }

                    var hit = false
                    for (i in parameterTypes.indices) {
                        val name = parameterTypes[i].name
                        if (name == "com.tencent.shadow.core.runtime.ShadowActivity") {
                            paramArray[i] =
                                "com.immomo.hani.molive.PluginKit.getActivity($${i + 1})"
                            parameterTypes[i] = mClassPool.get("android.app.Activity")
                            hit = true
                        }
                    }

                    if (hit) {
                        val params = Joiner.on(',').join(paramArray)
                        val clazz = classPool.getOrNull(className)

                        if (clazz == null || fakeClass.contains(className)) { // 调用宿主依赖Activity的方法
                            val methodID = "${ctMethod.name} ${ctMethod.signature}"

                            println("Javassit: $methodID")

                            makeNew(className, returnType, methodName, parameterTypes)

                            val code = String.format("$0.%1s( %2s);", methodName, params)
                            println("命中 $className $code")

                            methodCall.replace(code)
                        }
                    }
                }
            }

            private fun makeNew(
                className: String,
                returnType: CtClass?,
                methodName: String,
                params: Array<CtClass>
            ) {
                var body = "{return null;}"
                if (returnType == CtClass.voidType) {
                    body = "{return;}"
                }

                makeNewClazz(
                    classPool,
                    className,
                    Modifier.PUBLIC,
                    returnType,
                    methodName,
                    params,
                    null,
                    body
                )
            }
        })
    }

    /**
     * 宿主类不在classpath，javassit编译校验不过，给生成下桩代码
     */
    fun makeNewClazz(
        classPool: ClassPool,
        className: String,
        modifier: Int,
        returnType: CtClass?,
        methodName: String,
        parameterClassNames: Array<CtClass>,
        exceptions: Array<CtClass>?,
        body: String
    ) {
        val ctClass = classPool.makeClass(className);
        val addMethod =
            CtNewMethod.make(modifier, returnType, methodName, parameterClassNames, null, body, ctClass);
        ctClass.addMethod(addMethod);

        fakeClass.add(ctClass.name)
    }
}