package com.gswxxn.autonfc.hook

import android.content.Context
import android.nfc.NfcAdapter
import android.widget.Toast
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.bean.VariousClass
import com.highcapable.yukihookapi.hook.factory.configs
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onInit() = configs {
        debugTag = "AutoNFC"
        isDebug = false
    }

    override fun onHook() = encase {
        loadApp("com.miui.tsmclient") {

            val basePackage = "com.miui.tsmclient.ui.quick"

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

            "$basePackage.DoubleClickActivity".hook {
                injectMember {
                    method {
                        name = "onResume"
                        emptyParam()
                    }
                    afterHook {
                        NfcAdapter::class.java.method {
                            name = "getDefaultAdapter"
                            param(ContextClass)
                        }.get().call(appContext).let { nfcAdapter ->
                            nfcAdapterHelper("enable", nfcAdapter).call()

                            MainScope().launch {
                                waitNFCEnable(appContext, nfcAdapter)
                                field { type(VariousClass(
                                    "$basePackage.p",
                                    "$basePackage.SwitchCardFragment"
                                )).index().first() }.get(instance).setNull()
                                method.invokeOriginal<Unit>()
                            }
                        }
                    }
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
        val isEnabled = NfcAdapter::class.java.method {
            name = "isEnabled"
            emptyParam()
        }.get(nfcAdapter)

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