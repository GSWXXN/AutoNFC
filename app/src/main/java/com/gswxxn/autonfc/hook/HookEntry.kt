package com.gswxxn.autonfc.hook

import android.content.Context
import android.nfc.NfcAdapter
import android.widget.Toast
import com.gswxxn.autonfc.BuildConfig
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.configs
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
                            if (nfcAdapterHelper("isEnabled", nfcAdapter).boolean()) return@afterHook

                            nfcAdapterHelper("enable", nfcAdapter).call()

                            MainScope().launch {
                                waitNFCEnable(appContext!!, nfcAdapter)
                                val ctaHelper = "$packageName.entity.CTAHelper".toClass()
                                field { type(BooleanType).index().last() }.get(instance).setFalse()
                                ctaHelper.method {
                                    name = "check"
                                    emptyParam()
                                }.get(field { type = ctaHelper }.get(instance).any()).call()
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

    private suspend fun waitNFCEnable(context : Context, nfcAdapter : Any?) {
        val isEnabled = nfcAdapterHelper("isEnabled", nfcAdapter)

        repeat(15) {
            if (!isEnabled.boolean())
                delay(300)
            else {
                return@repeat
            }

            if (it == 14)
                Toast.makeText(context, "自动开启 NFC 失败", Toast.LENGTH_SHORT).show()
        }
    }
}