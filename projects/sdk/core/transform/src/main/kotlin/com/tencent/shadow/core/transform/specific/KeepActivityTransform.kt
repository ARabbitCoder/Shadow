package com.tencent.shadow.core.transform.specific

import com.google.common.base.Joiner
import com.tencent.shadow.core.transform_kit.SpecificTransform
import com.tencent.shadow.core.transform_kit.TransformStep
import javassist.*
import javassist.bytecode.Descriptor
import javassist.bytecode.MethodInfo
import javassist.expr.ExprEditor
import javassist.expr.MethodCall
import net.bytebuddy.jar.asm.*
import java.io.ByteArrayInputStream
import java.io.IOException

class KeepActivityTransform : SpecificTransform() {
    override fun setup(allInputClass: Set<CtClass>) {
        newStep(object : TransformStep {
            override fun filter(allInputClass: Set<CtClass>): Set<CtClass> {
                return allInputClass
            }

            override fun transform(ctClass: CtClass) {
                println("Keep检测 ${ctClass.name}")

                var isNeedTransform = false

                ctClass.declaredMethods.forEach { ctMethod ->
                    if (ctMethod.hasAnnotation("androidx.annotation.Keep")) {
                        isNeedTransform = true
                        try {
                            //renameMethod(ctMethod, mClassPool);
                        } catch (e: NotFoundException) {
                            e.printStackTrace();
                        } catch (e: CannotCompileException) {
                            e.printStackTrace();
                        } catch (e: IOException) {
                            e.printStackTrace();
                        }
                    }
                }

                if (isNeedTransform) {
                    try {
                        val bytes = mClassPool.get(ctClass.name).toBytecode()
                        val cw = ClassWriter(ClassReader(bytes), ClassWriter.COMPUTE_MAXS)
                        ClassReader(bytes).accept(MyClassVisitor(cw), ClassReader.EXPAND_FRAMES)
                        ctClass.defrost()
                        mClassPool.makeClass(ByteArrayInputStream(cw.toByteArray())) //重新写入classpool，不然不生效
                    } catch (e: IOException) {
                        e.printStackTrace()
                    } catch (e: CannotCompileException) {
                        e.printStackTrace()
                    }
                }
            }
        })
    }

    @Throws(NotFoundException::class, CannotCompileException::class, IOException::class)
    fun renameMethod(ctMethod: CtMethod, classPool: ClassPool?) {
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
                val methodInfo2: MethodInfo? = null
                try {
                    val parameterTypes = Descriptor.getParameterTypes(signature, classPool)
                    if (parameterTypes != null) {
                        val paramArray = arrayOfNulls<String>(parameterTypes.size)
                        for (i in parameterTypes.indices) {
                            paramArray[i] = "$${i+1}"
                        }
                        var isNeed = false
                        for (i in parameterTypes.indices) {
                            val name = parameterTypes[i].classFile.name
                            println("$name: $methodName")
                            if (name == "com.tencent.shadow.core.runtime.ShadowActivity" || name == "android.app.Activity") {
                                paramArray[i] = "com.immomo.hani.molive.AppManager2.getActivity()"
                                isNeed = true
                            }
                        }
                        if (isNeed) {
                            val params = Joiner.on(',').join(paramArray)
                            val s1 = String.format("$0.%1s( %2s);", methodName, params)
                            println("命中 $className $s1")

                            methodCall.replace(s1)
                        }
                    }
                } catch (e: NotFoundException) {
                    e.printStackTrace()
                }
            }
        })
    }

    internal inner class MyClassVisitor(api: ClassVisitor?) : ClassVisitor(Opcodes.ASM6, api) {
        override fun visitMethod(
            access: Int,
            name: String?,
            desc: String?,
            signature: String?,
            exceptions: Array<String>?
        ): MethodVisitor {
            val methodVisitor = super.visitMethod(access, name, desc, signature, exceptions)
            return MyMethodVisitor(
                api,
                methodVisitor,
                access,
                name,
                desc,
                signature,
                exceptions
            )
        }

        private inner class MyMethodVisitor(
            api: Int,
            methodVisitor: MethodVisitor?,
            access: Int,
            name: String?,
            desc: String?,
            signature: String?,
            exceptions: Array<String>?
        ) : MethodVisitor(api, methodVisitor) {

            private var inject: Boolean = false

            override fun visitMethodInsn(
                opcode: Int,
                owner: String?,
                name: String?,
                desc: String?,
                itf: Boolean
            ) {
                var desc = desc
                if (inject) {
                    println("Keep方法调用 $owner $name $desc")
                    val oldDesc = desc
                    if (desc != null) {
                        val newDesc = desc.replace(
                            "Lcom/tencent/shadow/core/runtime/ShadowActivity",
                            "Landroid/app/Activity",
                            false
                        )
                        if (newDesc != oldDesc) {
                            desc = newDesc
                            println("命中 $owner $name $desc")
                        }
                    }
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf)
            }

            override fun visitAnnotation(desc: String?, visible: Boolean): AnnotationVisitor? {
                if ("Landroidx/annotation/Keep;" == desc) {
                    inject = true
                }
                return super.visitAnnotation(desc, visible)
            }
        }
    }
}