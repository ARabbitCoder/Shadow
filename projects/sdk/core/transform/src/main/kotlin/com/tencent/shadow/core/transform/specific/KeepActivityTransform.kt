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
                println("开始Keep")
                var cr: ClassReader? = null
                try {
                    cr = ClassReader(ctClass.toBytecode())
                    val cw = ClassWriter(cr, ClassWriter.COMPUTE_MAXS)
                    cr.accept(MyClassVisitor(cw), ClassReader.EXPAND_FRAMES)
                    ctClass.defrost()
                    val bytes = cw.toByteArray()
                    mClassPool.makeClass(ByteArrayInputStream(bytes))
                } catch (e: IOException) {
                    e.printStackTrace()
                } catch (e: CannotCompileException) {
                    e.printStackTrace()
                }


//                CtMethod[] declaredMethods = ctClass.getDeclaredMethods();
//                for (CtMethod ctMethod : declaredMethods) {
//
//                    if (ctMethod.hasAnnotation("androidx.annotation.Keep")) {
//                        try {
//                            renameMethod(ctMethod, mClassPool);
//                        } catch (NotFoundException e) {
//                            e.printStackTrace();
//                        } catch (CannotCompileException e) {
//                            e.printStackTrace();
//                        } catch (IOException e) {
//                            e.printStackTrace();
//                        }
//                    }
//                }
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
                            paramArray[i] = "$$i"
                        }
                        var isNeed = false
                        for (i in parameterTypes.indices) {
                            val name = parameterTypes[i].classFile.name
                            println("$name: $methodName")
                            if (name == "com.tencent.shadow.core.runtime.ShadowActivity") {
                                paramArray[i] = "com.immomo.hani.molive.AppManager2.getActivity()"
                                isNeed = true
                            }
                        }
                        if (isNeed) {
                            val method2 = methodName + "2"
                            val params = Joiner.on(',').join(paramArray)
                            val s1 = String.format("($0).%1s( %2s);", method2, params)
                            println("$className $s1")
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
            println(name)
            val methodVisitor = super.visitMethod(access, name, desc, signature, exceptions)
            if (name == "bindActivity") {
                println("开始Keep bindActivity ")
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
            return methodVisitor
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
            override fun visitMethodInsn(
                opcode: Int,
                owner: String,
                name: String,
                desc: String,
                itf: Boolean
            ) {
                var desc = desc
                println("$owner $name $desc")
                if (desc == "(Lcom/tencent/shadow/core/runtime/ShadowActivity;)V") {
                    super.visitMethodInsn(
                        Opcodes.INVOKESTATIC,
                        "com/immomo/hani/molive/AppManager",
                        "getActivity",
                        "()Landroid/app/Activity;",
                        false
                    )
                    desc = "(Landroid/app/Activity;)V"
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf)
            }

            override fun visitVarInsn(opcode: Int, `var`: Int) {
                super.visitVarInsn(opcode, `var`)
            }

            override fun visitLabel(label: Label) {
                super.visitLabel(label)
            }

            override fun visitLocalVariable(
                name: String,
                desc: String,
                signature: String,
                start: Label,
                end: Label,
                index: Int
            ) {
                super.visitLocalVariable(name, desc, signature, start, end, index)
            }

            override fun visitLdcInsn(cst: Any) {
                println(cst)
                super.visitLdcInsn(cst)
            }
        }
    }
}