package com.tencent.shadow.core.transform.specific;

import com.google.common.base.Joiner;
import com.tencent.shadow.core.transform_kit.SpecificTransform;
import com.tencent.shadow.core.transform_kit.TransformStep;

import net.bytebuddy.jar.asm.ClassReader;
import net.bytebuddy.jar.asm.ClassVisitor;
import net.bytebuddy.jar.asm.ClassWriter;
import net.bytebuddy.jar.asm.Label;
import net.bytebuddy.jar.asm.MethodVisitor;
import net.bytebuddy.jar.asm.Opcodes;

import org.jetbrains.annotations.NotNull;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Set;

import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.NotFoundException;
import javassist.bytecode.Descriptor;
import javassist.bytecode.MethodInfo;
import javassist.expr.ExprEditor;
import javassist.expr.MethodCall;

public class KeepActivityTransform extends SpecificTransform {
    @Override
    public void setup(@NotNull Set<? extends CtClass> allInputClass) {
        newStep(new TransformStep() {
            @NotNull
            @Override
            public Set<CtClass> filter(@NotNull Set<? extends CtClass> allInputClass) {
                return (Set<CtClass>) allInputClass;
            }

            @Override
            public void transform(@NotNull CtClass ctClass) {

                System.out.println("开始Keep");

                ClassReader cr = null;
                try {
                    cr = new ClassReader(ctClass.toBytecode());
                    ClassWriter cw = new ClassWriter(cr, ClassWriter.COMPUTE_MAXS);
                    cr.accept(new MyClassVisitor(cw),ClassReader.EXPAND_FRAMES);

                    ctClass.defrost();
                    byte[] bytes = cw.toByteArray();
                    mClassPool.makeClass(new ByteArrayInputStream(bytes));

                } catch (IOException | CannotCompileException e) {
                    e.printStackTrace();
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
        });
    }



    void renameMethod(CtMethod ctMethod, ClassPool classPool) throws NotFoundException, CannotCompileException, IOException {
        ctMethod.instrument(new ExprEditor() {
            @Override
            public boolean doit(CtClass clazz, MethodInfo minfo) throws CannotCompileException {
                return super.doit(clazz, minfo);
            }

            @Override
            public void edit(MethodCall methodCall) throws CannotCompileException {
                super.edit(methodCall);

                String methodName = methodCall.getMethodName();
                String className = methodCall.getClassName();
                String signature = methodCall.getSignature();
                MethodInfo methodInfo2 = null;

                try {
                    CtClass[] parameterTypes = Descriptor.getParameterTypes(signature, classPool);
                    if (parameterTypes != null) {

                        String[] paramArray = new String[parameterTypes.length];
                        for (int i = 0; i < parameterTypes.length; i++) {
                            paramArray[i] = "$" + i;
                        }

                        boolean isNeed = false;
                        for (int i = 0; i < parameterTypes.length; i++) {
                            String name = parameterTypes[i].getClassFile().getName();
                            System.out.println(name + ": " + methodName);
                            if (name.equals("com.tencent.shadow.core.runtime.ShadowActivity")) {
                                paramArray[i] = "com.immomo.hani.molive.AppManager2.getActivity()";
                                isNeed = true;
                            }
                        }

                        if (isNeed) {
                            String method2 = methodName + "2";
                            String params = Joiner.on(',').join(paramArray);
                            String s1 = String.format("($0).%1s( %2s);", method2, params);
                            System.out.println(className + " " + s1);
                            methodCall.replace(s1);
                        }
                    }

                } catch (NotFoundException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    class MyClassVisitor extends ClassVisitor {
        public MyClassVisitor(ClassVisitor api) {
            super(Opcodes.ASM6, api);
        }

        @Override
        public MethodVisitor visitMethod(int access, String name, String desc, String signature, String[] exceptions) {
            System.out.println(name);
            MethodVisitor methodVisitor = super.visitMethod(access, name, desc, signature, exceptions);
            if (name.equals("bindActivity")) {

                System.out.println("开始Keep bindActivity ");
                return new MyMethodVisitor(api, methodVisitor, access, name, desc, signature, exceptions);
            }
            return methodVisitor;
        }



        private class MyMethodVisitor extends MethodVisitor {
            public MyMethodVisitor(int api, MethodVisitor methodVisitor, int access, String name, String desc, String signature, String[] exceptions) {
                super(api, methodVisitor);
            }

            @Override
            public void visitMethodInsn(int opcode, String owner, String name, String desc, boolean itf) {
                System.out.println(owner + " " + name + " " + desc);

                if (desc.equals("(Lcom/tencent/shadow/core/runtime/ShadowActivity;)V")) {
                    super.visitMethodInsn(Opcodes.INVOKESTATIC, "com/immomo/hani/molive/AppManager", "getActivity", "()Landroid/app/Activity;", false);
                    desc = "(Landroid/app/Activity;)V";
                }
                super.visitMethodInsn(opcode, owner, name, desc, itf);
            }

            @Override
            public void visitVarInsn(int opcode, int var) {
                super.visitVarInsn(opcode, var);
            }

            @Override
            public void visitLabel(Label label) {
                super.visitLabel(label);
            }

            @Override
            public void visitLocalVariable(String name, String desc, String signature, Label start, Label end, int index) {
                super.visitLocalVariable(name, desc, signature, start, end, index);
            }

            @Override
            public void visitLdcInsn(Object cst) {
                System.out.println(cst);
                super.visitLdcInsn(cst);
            }
        }
    }

}
