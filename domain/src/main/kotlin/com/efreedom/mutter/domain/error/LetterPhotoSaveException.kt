package com.efreedom.mutter.domain.error

/**
 * 편지 사진 업로드(또는 새 편지의 사진 반영 update) 실패 — iOS `LetterPhotoSaveError` 대응.
 * 편지 행은 이미 있으므로 화면은 "사진을 올리지 못했어요…"를 보이고, 다음 저장은 같은 편지를 갱신한다
 * (모드 승격은 `LetterUseCase.saveWithPhotos`의 `onCreated`가 맡는다).
 */
class LetterPhotoSaveException(cause: Throwable) : Exception(cause)
