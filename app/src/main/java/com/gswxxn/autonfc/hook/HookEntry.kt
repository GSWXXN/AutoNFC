package com.gswxxn.autonfc.hook

import android.content.Context
import android.nfc.NfcAdapter
import android.os.UserHandle
import android.widget.Toast
import com.gswxxn.autonfc.BuildConfig
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.current
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.BundleClass
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@InjectYukiHookWithXposed
object HookEntry : IYukiHookXposedInit {
    override fun onInit() = configs {
        debugLog { tag = "AutoNFC" }
        isDebug = BuildConfig.DEBUG
    }

    override fun onHook() = encase {
        loadApp("com.miui.tsmclient") {

            resources().hook {
                injectResource {
                    conditions {
                        name = "nfc_off_hint"
                        string()
                    }
                    replaceTo("正在自动开启NFC...")
                }

                injectResource {
                    conditions {
                        name = "immediately_open"
                        string()
                    }
                    replaceTo("手动开启NFC")
                }
            }

            "$packageName.ui.quick.DoubleClickActivity".hook {
                injectMember {
                    method {
                        name = "onCreate"
                        param(BundleClass)
                    }
                    afterHook {
                        NfcAdapter::class.java.method {
                            name = "getDefaultAdapter"
                            param(ContextClass)
                        }.get().call(appContext).let { nfcAdapter ->
                            if (nfcAdapterHelper("isEnabled", nfcAdapter).boolean()) {
                                "android.provider.Settings\$Secure".toClass().method {
                                    name = "putStringForUser"
                                    paramCount(4)
                                }.get().call(
                                    appContext!!.contentResolver,
                                    "nfc_payment_default_component",
                                    "com.android.nfc/com.android.nfc.cardemulation.ESEWalletDummyService",
                                    UserHandle::class.java.method { name = "myUserId" }.get().invoke()
                                )
                                return@afterHook
                            }

                            nfcAdapterHelper("enable", nfcAdapter).call()

                            MainScope().launch {
                                if (waitNFCEnable(appContext!!, nfcAdapter)) {
                                    "android.provider.Settings\$Secure".toClass().method {
                                        name = "putStringForUser"
                                        paramCount(4)
                                    }.get().call(
                                        appContext!!.contentResolver,
                                        "nfc_payment_default_component",
                                        "com.android.nfc/com.android.nfc.cardemulation.ESEWalletDummyService",
                                        UserHandle::class.java.method { name = "myUserId" }.get().invoke()
                                    )
                                }

                                val isNewVersion = "$packageName.entity.CTAHelper".hasClass()

                                if (isNewVersion) {
                                    val ctaHelper = "$packageName.entity.CTAHelper".toClass()
                                    field { type(BooleanType).index().last() }.get(instance).setFalse()
                                    ctaHelper.method {
                                        name = "check"
                                        emptyParam()
                                    }.get(field { type = ctaHelper }.get(instance).any()).call()
                                } else if (instance.current().field { name = "mLoginStatus" }.boolean()) {
                                    instance.current().method { name = "requestShowSwitchCardFragment" }.call()
                                }

                            }
                        }
                    }
                }.onAllFailure {
                    Toast.makeText(appContext, "如遇卡在自动开启NFC界面，请点击\"手动开启NFC\"按钮再返回此界面临时解决，并向模块开发者反馈",Toast.LENGTH_SHORT).show()
                }

                injectMember {
                    method {
                        name = "onDestroy"
                        emptyParam()
                    }
                    beforeHook {
                        NfcAdapter::class.java.method {
                            name = "getDefaultAdapter"
                            param(ContextClass)
                        }.get().call(appContext).let { nfcAdapter ->
                            nfcAdapterHelper("disable", nfcAdapter).call()
                        }
                    }
                }
            }
        }
    }

    private fun nfcAdapterHelper(methodName: String, inst : Any?) =
        NfcAdapter::class.java.method {
            name = methodName
            emptyParam()
        }.get(inst)

    private suspend fun waitNFCEnable(context : Context, nfcAdapter : Any?): Boolean {
        val isEnabled = nfcAdapterHelper("isEnabled", nfcAdapter)

        repeat(15) {
            if (!isEnabled.boolean())
                delay(300)
            else {
                return true
            }

            if (it == 14)
                Toast.makeText(context, "自动开启 NFC 失败", Toast.LENGTH_SHORT).show()
        }
        return false
    }
}