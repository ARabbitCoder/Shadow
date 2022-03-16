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
import net.bytebuddy.jar.asm.*
import java.io.ByteArrayInputStream
import java.io.IOException

class KeepActivityTransform(private val emptyClass: Array<String>) : SpecificTransform() {

    val hitMethods = hashSetOf<String>()
    val fakeClass =  hashSetOf<CtClass?>()

    init {
        JavassistUtil.parseRulesAndMakeClass(emptyClass.toList(),mClassPool)
    }

    override fun setup(allInputClass: Set<CtClass>) {
        newStep(object : TransformStep {
            override fun filter(allInputClass: Set<CtClass>): Set<CtClass> {
                return allInputClass
            }

            override fun transform(ctClass: CtClass) {
//                println("Keep检测 ${ctClass.name}")

                hitMethods.clear()

                ctClass.declaredMethods.forEach { ctMethod ->
                    try {
                        renameMethod(ctClass, ctMethod, mClassPool);
                    } catch (e: NotFoundException) {
                        e.printStackTrace();
                    } catch (e: CannotCompileException) {
                        e.printStackTrace();
                    } catch (e: IOException) {
                        e.printStackTrace();
                    }
                }

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
        })
    }

    @Throws(NotFoundException::class, CannotCompileException::class, IOException::class)
    fun renameMethod(ctClass: CtClass, ctMethod: CtMethod, classPool: ClassPool) {
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

                val returnType: CtClass? = try {
                    Descriptor.getReturnType(signature, classPool)
                } catch (e: NotFoundException) {
                    mClassPool.makeClass(e.message)
                }
                fakeClass.add(returnType)

                val parameterTypes = Descriptor.getParameterTypes(signature, classPool)

                if (parameterTypes != null) {

                    val paramArray = arrayOfNulls<String>(parameterTypes.size)
                    for (i in parameterTypes.indices) {
                        paramArray[i] = "$${i + 1}"
                    }

                    var hit = false
                    for (i in parameterTypes.indices) {
                        val name = parameterTypes[i].name
                        if (name == "com.tencent.shadow.core.runtime.ShadowActivity" || name == "android.app.Activity") {
                            paramArray[i] =
                                "com.immomo.hani.molive.PluginKit.getActivity($${i + 1})"
                            parameterTypes[i] = mClassPool.get("android.app.Activity")
                            hit = true
                        }
                    }

                    if (hit) {
                        val params = Joiner.on(',').join(paramArray)
                        try {
                            println("Javassit: ${ctClass.name}")
                            classPool.get(className)

                        } catch (e: NotFoundException) { // 调用宿主依赖Activity的方法

                            val methodID = "${ctMethod.name} ${ctMethod.signature}"
                            hitMethods.add(methodID)
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

        fakeClass.add(ctClass)
    }

    internal inner class MyClassVisitor(api: ClassVisitor?) : ClassVisitor(Opcodes.ASM6, api) {
        override fun visitMethod(
            access: Int,
            name: String?,
            desc: String?,
            signature: String?,
            exceptions: Array<String>?
        ): MethodVisitor {
            val methodID = "$name $signature"
            println("ASM: $methodID")
            val methodVisitor = super.visitMethod(access, name, desc, signature, exceptions)
            if (hitMethods.contains(methodID)) {
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
                owner: String?,
                name: String?,
                desc: String?,
                itf: Boolean
            ) {
                var desc = desc
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
                super.visitMethodInsn(opcode, owner, name, desc, itf)
            }
        }
    }
}