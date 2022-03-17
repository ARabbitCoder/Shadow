package com.tencent.shadow.core.transform

import javassist.*
import javassist.bytecode.Descriptor

object JavassistUtil {
    //"com.sample.test.Empty:public:void:test:(com.sample.test.Param1,com.sample.test.Param2)"

    fun parseRulesAndMakeClass(emptyRules:List<String>,classPool: ClassPool){
        makeClazzs(classPool, parseRules(emptyRules,classPool))
    }

    fun makeClazzs(classPool: ClassPool,rules:List<EmptyRule>){
        for (rule in rules){
            makeNewClazz(classPool,rule.className,rule.modifier,rule.returnType,rule.methodName,rule.parameterClassNames,null)
        }

    }

    fun makeNewClazz(classPool: ClassPool,className:String,modifier:Int,returnType:CtClass,methodName:String,parameterClassNames:List<String>?, exceptions:Array<CtClass>?){
        val ctClass = classPool.makeClass(className);
        var parameters:Array<CtClass?>? = null
        if(parameterClassNames!=null && parameterClassNames.isNotEmpty()){
            parameters= Array(parameterClassNames.size){null}
            for(i in parameterClassNames.indices){
                val classname = parameterClassNames[i]
                var paclass = classPool.getOrNull(classname)
                if(paclass == null){
                    paclass = classPool.makeClass(classname)
                }
                parameters[i] = paclass
            }
        }
        var body = "{}"
        body = if(returnType == CtClass.voidType){
            "{return;}"
        }else{
            "{return null;}"
        }
        val addMethod = CtNewMethod.make(modifier, returnType, methodName, parameters, exceptions, body, ctClass);
        ctClass.addMethod(addMethod)
    }

    fun makeNewClazz(classPool: ClassPool,className:String,modifier:Int,returnType:CtClass,methodName:String,parameterClassNames:List<String>?, exceptions:Array<CtClass>,body:String){
        val ctClass = classPool.makeClass(className);
        var parameters:Array<CtClass?>? = null
        if(parameterClassNames!=null && parameterClassNames.isNotEmpty()){
            parameters= Array(parameterClassNames.size){null}
            for(i in parameterClassNames.indices){
                val classname = parameterClassNames[i]
                var paclass = classPool.getOrNull(classname)
                if(paclass == null){
                    paclass = classPool.makeClass(classname)
                }
                parameters[i] = paclass
            }
        }
        val addMethod = CtNewMethod.make(modifier, returnType, methodName, parameters, exceptions, "{}", ctClass);
        ctClass.addMethod(addMethod)
    }

    fun makeNewClazz(classPool: ClassPool,className:String,modifier:Int,returnType:String,methodName:String,parameterClassNames:List<String>?, exceptions:Array<CtClass>,body:String){
        val ctClass = classPool.makeClass(className);
        var parameters:Array<CtClass?>? = null
        if(parameterClassNames!=null && parameterClassNames.isNotEmpty()){
            parameters= Array(parameterClassNames.size){null}
            for(i in parameterClassNames.indices){
                val classname = parameterClassNames[i]
                var paclass = classPool.getOrNull(classname)
                if(paclass == null){
                    paclass = classPool.makeClass(classname)
                }
                parameters[i] = paclass
            }
        }

        val addMethod = CtNewMethod.make(modifier, parseReturnType(returnType,classPool), methodName, parameters, exceptions, "{}", ctClass);
        ctClass.addMethod(addMethod)
    }

    fun parseRules(emptyRules:List<String>,classPool: ClassPool):List<EmptyRule>{
        val emptyRuleList = mutableListOf<EmptyRule>()
        for(emptyClass in emptyRules){
            if(emptyClass.isNotEmpty()){
                val rule = parseEmptyRule(emptyClass,classPool)
                rule?.let {
                    emptyRuleList.add(it)
                }
            }
        }
        return emptyRuleList
    }

    fun parseEmptyRule(config:String,classPool: ClassPool): EmptyRule?{
        val configStrs = config.split(":")
        if(configStrs.size==5){
            return EmptyRule(configStrs[0],
                parseModifierType(configStrs[1]),
                parseReturnType(configStrs[2],classPool),configStrs[3],
                parseParams(configStrs[4])
            )
        }
        return null
    }

    fun parseParams(paramStr:String):List<String>?{
        val realParam = paramStr.replace("(","").replace(")","")
        if(realParam.isEmpty()){
            return null
        }
        return realParam.split(",").toList()
    }
    fun parseReturnType(returnType:String,mClassPool: ClassPool): CtClass {
        when(returnType){
            "void"-> return CtClass.voidType
            "Boolean"-> return CtClass.booleanType
            "Int"-> return CtClass.intType
            "Long"-> return CtClass.longType
            "Float"-> return CtClass.floatType
            "Char"-> return CtClass.charType
            "Short"-> return CtClass.shortType
            "Double"-> return CtClass.doubleType
        }
        var getReturn = mClassPool.getOrNull(returnType)
        if(getReturn==null){
            getReturn = mClassPool.makeClass(returnType)
        }
        return getReturn
    }

    fun parseModifierType(modifier:String):Int{
        when(modifier){
            "public"-> return Modifier.PUBLIC
            "private"-> return Modifier.PRIVATE
            "public static"-> return (Modifier.PUBLIC or Modifier.STATIC)
        }
        return Modifier.PUBLIC
    }


    fun getParameterTypesExt(signature:String,classPool:ClassPool,fakeClass:HashSet<String?>):Array<CtClass>{
        return try {
            val parameterTypes = Descriptor.getParameterTypes(signature, classPool)
            parameterTypes
        }catch (e:NotFoundException){
            println("$signature not find class:${e.message}")
            classPool.makeClass(e.message)
            fakeClass.add(e.message)
            getParameterTypesExt(signature, classPool, fakeClass)
        }
    }

    data class EmptyRule(
        val className: String, val modifier:Int, val returnType: CtClass, val methodName: String, val parameterClassNames:List<String>?
    )
}