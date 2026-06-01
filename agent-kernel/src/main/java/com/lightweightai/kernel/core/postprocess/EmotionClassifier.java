package com.lightweightai.kernel.core.postprocess;

/**
 * R3 — 逐块情绪来源（可插拔）。
 *
 * 给定一个可朗读小句，返回其情绪标签（如 neutral / calm / cheerful / excited / sad / empathetic …），
 * 供 {@link SpeakableChunkProcessor} 标注到 SPEAKABLE_CHUNK，下游 Voice Gateway 用它选 TTS 语气、
 * 设备 Realizer 用它驱动眼睛/LED。
 *
 * 实现可以是本地启发式（标点/关键词，零延迟）或更重的模型；具体选型留给装配方，本接口只定契约。
 */
@FunctionalInterface
public interface EmotionClassifier {

    String classify(String text);

    /** 默认中性分类器 —— 不引入额外延迟，始终返回 neutral。*/
    EmotionClassifier NEUTRAL = text -> "neutral";
}
