package com.tencent.shadow.core.transform.specific

import com.tencent.shadow.core.transform_kit.SpecificTransform
import com.tencent.shadow.core.transform_kit.TransformStep
import javassist.*
import javassist.bytecode.Descriptor
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import java.io.File
import java.io.IOException

class ScanMissMatchParamTransform : SpecificTransform() {

    val file = File("missMatchInfo.txt").apply {
        writeText(System.currentTimeMillis().toString() + "\n")
    }

    override fun setup(allInputClass: Set<CtClass>) {

        newStep(object : TransformStep {
            override fun filter(allInputClass: Set<CtClass>): Set<CtClass> {
                return allInputClass
            }

            override fun transform(ctClass: CtClass) {
                ctClass.declaredMethods.forEach { ctMethod ->
                    try {
                        if (ctMethod != null) {
                            scanMethod(ctClass, ctMethod, mClassPool);
                        }
                    } catch (e: NotFoundException) {
                        e.printStackTrace();
                    } catch (e: CannotCompileException) {
                        e.printStackTrace();
                    } catch (e: IOException) {
                        e.printStackTrace();
                    }
                }
            }
        })
    }

    private fun scanMethod(ctClass: CtClass, ctMethod: CtMethod, classPool: ClassPool) {

//        println("${ctClass.name} { ${ctMethod.name} }")

        ctMethod.instrument(object : ExprEditor(){
            override fun edit(methodCall: MethodCall) {
                super.edit(methodCall)
                val methodName = methodCall.methodName
                val className = methodCall.className
                val signature = methodCall.signature

                val parameterTypes = Descriptor.getParameterTypes(signature, classPool)
                if (parameterTypes != null) {
                    for (i in parameterTypes.indices) {
                        val name = parameterTypes[i].name
//                        println("$name: $methodName")
                        if (name == "com.tencent.shadow.core.runtime.ShadowActivity") {
                            val text = "${ctClass.name} { ${ctMethod.name} } :  $className.$methodName() \n"
                            try {
                                classPool.get(className)
                            } catch (e: NotFoundException) {
                                print("write $text")
                                file.appendText(text)
                            }
                        }
                    }
                }
            }
        })
    }
}