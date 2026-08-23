# R8 규칙 (release, isMinifyEnabled = true)
#
# 이 앱이 R8에서 깨질 수 있는 지점은 사실상 하나다 — kotlinx.serialization.
# Supabase/PostgREST 응답은 전부 @Serializable DTO로 역직렬화되므로(LetterRow, LetterPayloadDto,
# InboxRow, ProfileDto …) 직렬화기 조회가 실패하면 편지 목록·열람·로그인이 런타임에 통째로 죽는다.
# 컴파일 에러가 아니라 릴리스에서만 나는 실패라 규칙으로 미리 막는다.
#
# Ktor·Supabase-kt·Kakao SDK·Firebase는 각자 consumer proguard 규칙을 AAR에 담아 배포하므로
# 여기에 다시 적지 않는다(중복 규칙은 축소 효과만 떨어뜨린다).

# ── kotlinx.serialization (공식 README 권장 규칙) ────────────────────────────
# @Serializable 클래스의 Companion 필드 유지.
-if @kotlinx.serialization.Serializable class **
-keepclassmembers class <1> {
    static <1>$Companion Companion;
}

# Companion의 serializer() 유지.
-if @kotlinx.serialization.Serializable class ** {
    static **$* *;
}
-keepclassmembers class <2>$<3> {
    kotlinx.serialization.KSerializer serializer(...);
}

# @Serializable object의 INSTANCE.serializer() 유지.
-if @kotlinx.serialization.Serializable class ** {
    public static ** INSTANCE;
}
-keepclassmembers class <1> {
    public static <1> INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}

# 직렬화기 조회가 런타임 애노테이션을 본다.
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault

# ── 제네릭 시그니처 (Retrofit 필수) ─────────────────────────────────────────
# 카카오 SDK가 내부적으로 Retrofit을 쓴다. Retrofit은 리플렉션으로 반환 타입의 제네릭을 읽어
# call adapter를 만들기 때문에, Signature 속성이 지워지면 Call<Foo>가 raw Call로 보이고
#   "Call return type must be parameterized as Call<Foo>"
# 로 앱이 실행 즉시 죽는다(실제로 이 규칙 없이 런치 크래시를 확인했다).
-keepattributes Signature, InnerClasses, EnclosingMethod

# Retrofit 서비스 인터페이스와 그 메서드 시그니처 유지.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
-if interface * { @retrofit2.http.** <methods>; }
-keep,allowobfuscation interface <1>

# R8 full mode(AGP 8 기본값)는 "유지되지 않는" 클래스의 제네릭 시그니처까지 지운다.
# 그래서 Signature 속성만 남겨도 Call<Foo>의 Foo가 사라져 위 크래시가 계속 난다.
# 축소·난독화는 허용하고 타입만 유지하면 되므로 allowshrinking/allowobfuscation을 붙인다.
# (Retrofit 공식 R8 full mode 권장 규칙)
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
# suspend 함수는 Continuation으로 감싸지며 그 타입 인자가 반환 타입으로 쓰인다.
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation

# ── 카카오 SDK (공식 권장 규칙) ─────────────────────────────────────────────
# 카카오 SDK는 Gson으로 응답을 역직렬화하고, 에러 원인은 열거형 상수 '이름'을 리플렉션으로 찾는다
# (com.kakao.sdk.common.model.AuthErrorCause / ApiErrorCause).
# 필드 이름이 난독화되면 java.lang.NoSuchFieldException: TokenNotFound 로 앱이 죽는다
# (실제로 이 규칙 없이 런치 크래시를 확인했다).
-keep class com.kakao.sdk.**.model.* { <fields>; }
-keep class * extends com.google.gson.TypeAdapter

# ── 크래시 로그 가독성 ──────────────────────────────────────────────────────
# 스택트레이스의 원래 파일·줄 번호 보존. Play Console에 매핑 파일이 자동 업로드되지만
# 이 속성이 없으면 난독화 해제 후에도 줄 번호가 사라진다.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
