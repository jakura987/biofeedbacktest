package com.intellizon.biofeedbacktest.wifi.connect

import io.reactivex.Completable
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Single

/** 负责 Wi-Fi 热点（SoftAP）相关*/
interface WifiCommunicationManager {

    /** 开启热点（不传则用默认配置） */
    fun enableAp(cfg: WifiApConfig = WifiApConfig.DEFAULT): Completable

    /** 关闭热点 */
    fun disableAp(): Completable


    /** 读取当前热点状态（一次性） */
    fun readApStatus(): Single<WifiApStatus>


    /** 列出本机私网 IPv4（用于给从机连 AP 后选择网关/目标） */
    fun listPrivateIPv4(): Single<String>

    /** 返回一个最可能的网关候选 IP（比如 ap0 的地址） */
    fun bestGatewayCandidate(): Maybe<String>

    /**
     * 轮询式观察 AP 状态（有的厂商 API 无回调，只能轮询）
     * @param pollMs 轮询间隔，默认 1500ms（兼顾“点击后系统起 AP 需要的时间”）
     */
    fun observeApStatus(pollMs: Long = 1500): Flowable<WifiApStatus>
}
