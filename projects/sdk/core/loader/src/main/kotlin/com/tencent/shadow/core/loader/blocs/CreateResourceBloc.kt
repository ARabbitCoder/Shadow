/*
 * Tencent is pleased to support the open source community by making Tencent Shadow available.
 * Copyright (C) 2019 THL A29 Limited, a Tencent company.  All rights reserved.
 *
 * Licensed under the BSD 3-Clause License (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *     https://opensource.org/licenses/BSD-3-Clause
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package com.tencent.shadow.core.loader.blocs

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.res.AssetManager
import android.content.res.Resources
import android.os.Handler
import android.os.Looper
import android.webkit.WebView
import java.io.File
import java.lang.reflect.Method
import java.util.concurrent.CountDownLatch

object CreateResourceBloc {
    fun create(archiveFilePath: String, hostAppContext: Context): Resources {
        //先用宿主context初始化一个WebView，以便WebView的逻辑去修改sharedLibraryFiles，将webview.apk添加进去
        val latch = CountDownLatch(1)
        Handler(Looper.getMainLooper()).post {
            WebView(hostAppContext)
            latch.countDown()
        }
        latch.await()

        val packageManager = hostAppContext.packageManager
        val applicationInfo = ApplicationInfo()
        applicationInfo.packageName = hostAppContext.applicationInfo.packageName
        applicationInfo.uid = hostAppContext.applicationInfo.uid
        applicationInfo.publicSourceDir = archiveFilePath
        applicationInfo.sourceDir = archiveFilePath
        applicationInfo.sharedLibraryFiles = hostAppContext.applicationInfo.sharedLibraryFiles
        try {
            return createResources(hostAppContext,archiveFilePath)
            //return packageManager.getResourcesForApplication(applicationInfo)
        } catch (e: PackageManager.NameNotFoundException) {
            throw RuntimeException(e)
        }

    }



    fun createResources(context:Context,  pluginApkPath: String):Resources{
        val AssetManagerClass: Class<out AssetManager?> = AssetManager::class.java
        val assetManager: AssetManager? = AssetManagerClass.newInstance()
        // 将插件资源和宿主资源通过 addAssetPath方法添加进去
        val addAssetPathMethod: Method = AssetManagerClass.getDeclaredMethod("addAssetPath", String::class.java)
        addAssetPathMethod.setAccessible(true)
        val hostResourcePath = context.packageResourcePath
        val result_1 = addAssetPathMethod.invoke(assetManager, hostResourcePath) as Int
        val result_2 = addAssetPathMethod.invoke(assetManager, pluginApkPath) as Int
        // 接下来创建，合并资源后的Resource
        val resources = Resources(
            assetManager,
            context.resources.displayMetrics,
            context.resources.configuration
        )
        return resources
    }


}
