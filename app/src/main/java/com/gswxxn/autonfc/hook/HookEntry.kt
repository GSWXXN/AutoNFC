package com.gswxxn.autonfc.hook

import android.nfc.NfcAdapter
import android.widget.Toast
import com.highcapable.yukihookapi.annotation.xposed.InjectYukiHookWithXposed
import com.highcapable.yukihookapi.hook.factory.encase
import com.highcapable.yukihookapi.hook.factory.method
import com.highcapable.yukihookapi.hook.type.android.ContextClass
import com.highcapable.yukihookapi.hook.type.java.BooleanType
import com.highcapable.yukihookapi.hook.xposed.proxy.IYukiHookXposedInit

@InjectYukiHookWithXposed
class HookEntry : IYukiHookXposedInit {
    override fun onHook() = encase {
        loadApp("com.miui.tsmclient") {
            findClass("com.miui.tsmclient.ui.quick.DoubleClickActivity").hook {
                injectMember {
                    method {
                        name = "onWindowFocusChanged"
                        param(BooleanType)
                    }
                    beforeHook {
                        val isEnable = args(0).boolean()
                        NfcAdapter::class.java.method {
                            name = "getDefaultAdapter"
                            param(ContextClass)
                        }.get().call(appContext).let { nfcAdapter ->
                            when {
                                !isEnable -> nfcAdapterHelper("disable", nfcAdapter).call()
                                isEnable && !nfcAdapterHelper("isEnabled", nfcAdapter).boolean() ->
                                    nfcAdapterHelper("enable", nfcAdapter).call()
                            }
                        }
                    }
                }

                injectMember {
                    method {
                        name = "onResume"
                        emptyParam()
                    }
                    beforeHook {
                        NfcAdapter::class.java.method {
                            name = "getDefaultAdapter"
                            param(ContextClass)
                        }.get().call(appContext).let { nfcAdapter ->
                            nfcAdapterHelper("enable", nfcAdapter).call()

                            val isEnabled = NfcAdapter::class.java.method {
                                name = "isEnabled"
                                emptyParam()
                            }.get(nfcAdapter)

                            repeat(15) {
                                if (!isEnabled.boolean())
                                    Thread.sleep(300)
                                else return@repeat

                                if (it == 14)
                                    Toast.makeText(appContext, "自动开启 NFC 失败", Toast.LENGTH_SHORT).show()
                            }
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
}