package io.github.hnoni777.newdatemapdiary

import android.app.Application
import com.kakao.sdk.common.KakaoSdk
import com.kakao.vectormap.KakaoMapSdk

class NewDateMapDiaryApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        
        // 🗺️ 카카오 지도 SDK 초기화 (앱 실행 시 1회만 수행하면 됨)
        KakaoMapSdk.init(this, "6cc7070982d3684fcac142f3f8f4a691")
        
        // 🔗 카카오톡 공유 SDK 초기화 (Native App Key)
        KakaoSdk.init(this, "6cc7070982d3684fcac142f3f8f4a691")
    }
}
