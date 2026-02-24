/**
 * 语音服务 - ASR 语音识别 + 情感化 TTS 播报
 *
 * ASR: HarmonyOS SpeechKit speechRecognizer
 * TTS: HarmonyOS SpeechKit textToSpeech，根据情绪动态调整音速/音调
 */
import { speechRecognizer } from '@kit.SpeechKit';
import { textToSpeech } from '@kit.SpeechKit';
import { BusinessError } from '@kit.BasicServicesKit';

// 情绪 → TTS 参数映射
interface TtsParams {
  speed: number;   // 0.5 ~ 2.0
  pitch: number;   // 0.5 ~ 2.0
  volume: number;  // 0.0 ~ 2.0
}

const EMOTION_TTS_MAP: Record<string, TtsParams> = {
  '温柔': { speed: 0.92, pitch: 0.95, volume: 1.0 },
  '积极': { speed: 1.05, pitch: 1.05, volume: 1.0 },
  '开心': { speed: 1.08, pitch: 1.08, volume: 1.1 },
  '担忧': { speed: 0.88, pitch: 0.90, volume: 0.95 },
  '悲伤': { speed: 0.82, pitch: 0.88, volume: 0.9 },
  '平静': { speed: 1.0,  pitch: 1.0,  volume: 1.0 },
};

const DEFAULT_TTS: TtsParams = { speed: 0.95, pitch: 0.95, volume: 1.0 };

export type AsrResultCallback = (text: string, isFinal: boolean) => void;
export type AsrStateCallback = (recording: boolean) => void;
export type TtsStateCallback = (speaking: boolean) => void;

export class VoiceService {
  private static asrEngine: speechRecognizer.SpeechRecognitionEngine | null = null;
  private static ttsEngine: textToSpeech.TextToSpeechEngine | null = null;
  private static isAsrReady: boolean = false;
  private static isTtsReady: boolean = false;
  private static ttsStateCallback: TtsStateCallback | null = null;

  // ==================== 初始化 ====================

  static async initASR(): Promise<boolean> {
    if (VoiceService.isAsrReady) return true;
    try {
      const params: speechRecognizer.CreateEngineParams = {
        language: 'zh-CN',
        online: 1,
        extraParams: {}
      };
      VoiceService.asrEngine = await speechRecognizer.createEngine(params);
      VoiceService.isAsrReady = true;
      console.info('[VoiceService] ASR engine ready');
      return true;
    } catch (err) {
      const e = err as BusinessError;
      console.error(`[VoiceService] ASR init failed: ${e.code} - ${e.message}`);
      return false;
    }
  }

  static async initTTS(): Promise<boolean> {
    if (VoiceService.isTtsReady) return true;
    try {
      const params: textToSpeech.CreateEngineParams = {
        language: 'zh-CN',
        person: 0,
        online: 1,
        extraParams: { style: 'interaction-multidialogue', locate: 'CN' }
      };
      VoiceService.ttsEngine = await textToSpeech.createEngine(params);
      VoiceService.isTtsReady = true;
      console.info('[VoiceService] TTS engine ready');
      return true;
    } catch (err) {
      const e = err as BusinessError;
      console.error(`[VoiceService] TTS init failed: ${e.code} - ${e.message}`);
      return false;
    }
  }

  // ==================== ASR ====================

  /**
   * 开始录音识别
   * @param onResult  识别结果回调（实时 + 最终）
   * @param onState   录音状态变化回调
   */
  static async startListening(
    onResult: AsrResultCallback,
    onState: AsrStateCallback
  ): Promise<void> {
    const ready = await VoiceService.initASR();
    if (!ready || !VoiceService.asrEngine) {
      console.error('[VoiceService] ASR not available');
      return;
    }

    const engine = VoiceService.asrEngine;

    // 设置识别监听
    engine.setListener({
      onStart: (sessionID: string, _msg: string) => {
        console.info(`[ASR] Started, session: ${sessionID}`);
        onState(true);
      },
      onEvent: (_sessionID: string, _eventCode: number, _msg: string) => {},
      onResult: (sessionID: string, result: speechRecognizer.SpeechRecognitionResult) => {
        const text = result.result;
        const isFinal = result.isLast;
        console.info(`[ASR] Result: "${text}", isFinal: ${isFinal}`);
        onResult(text, isFinal);
      },
      onComplete: (_sessionID: string, _msg: string) => {
        console.info('[ASR] Completed');
        onState(false);
      },
      onError: (_sessionID: string, errorCode: number, errorMessage: string) => {
        console.error(`[ASR] Error: ${errorCode} - ${errorMessage}`);
        onState(false);
      }
    });

    // 启动识别
    const sessionID = Date.now().toString();
    try {
      engine.startListening({
        sessionID: sessionID,
        audioInfo: {
          audioType: 'pcm',
          sampleRate: 16000,
          soundChannel: 1,
          sampleBit: 16
        },
        extraParams: {
          sensibility: 1,
          vadBegin: 2000,
          vadEnd: 3000,
          maxAudioDuration: 30000
        }
      });
    } catch (err) {
      const e = err as BusinessError;
      console.error(`[VoiceService] Start listening failed: ${e.code} - ${e.message}`);
      onState(false);
    }
  }

  /**
   * 停止录音识别
   */
  static stopListening(): void {
    if (VoiceService.asrEngine) {
      try {
        VoiceService.asrEngine.finish({ sessionID: '' });
      } catch (err) {
        // ignore
      }
    }
  }

  // ==================== TTS ====================

  /**
   * 设置 TTS 状态回调
   */
  static setTtsStateCallback(cb: TtsStateCallback): void {
    VoiceService.ttsStateCallback = cb;
  }

  /**
   * 朗读文本（情感化）
   * @param text    要朗读的文本
   * @param emotion 情绪标签，用于调整语速/音调
   */
  static async speak(text: string, emotion?: string): Promise<void> {
    if (!text?.trim()) return;

    const ready = await VoiceService.initTTS();
    if (!ready || !VoiceService.ttsEngine) {
      console.error('[VoiceService] TTS not available');
      return;
    }

    // 先停止当前播放
    VoiceService.stopSpeaking();

    const params = (emotion && EMOTION_TTS_MAP[emotion]) ? EMOTION_TTS_MAP[emotion] : DEFAULT_TTS;
    const requestId = Date.now().toString();

    const speakParams: textToSpeech.SpeakParams = {
      requestId: requestId,
      extraParams: {
        speed: params.speed,
        pitch: params.pitch,
        volume: params.volume,
        languageContext: 'zh-CN',
        audioType: 'pcm'
      }
    };

    const callback: textToSpeech.SpeakCallback = {
      onStart: (reqId: string, _response: string) => {
        console.info(`[TTS] Speaking started, emotion: ${emotion}, speed: ${params.speed}`);
        VoiceService.ttsStateCallback?.(true);
      },
      onComplete: (reqId: string, _response: string) => {
        console.info('[TTS] Speaking completed');
        VoiceService.ttsStateCallback?.(false);
      },
      onStop: (reqId: string, _response: string) => {
        console.info('[TTS] Speaking stopped');
        VoiceService.ttsStateCallback?.(false);
      },
      onData: (_reqId: string, _audio: ArrayBuffer, _response: string) => {
        // PCM 数据（引擎内部处理）
      },
      onError: (reqId: string, errorCode: number, errorMessage: string) => {
        console.error(`[TTS] Error: ${errorCode} - ${errorMessage}`);
        VoiceService.ttsStateCallback?.(false);
      }
    };

    try {
      // 截断过长文本（TTS 有长度限制）
      const maxLen = 500;
      const speakText = text.length > maxLen ? text.substring(0, maxLen) + '。' : text;
      VoiceService.ttsEngine.speakText(speakText, speakParams, callback);
    } catch (err) {
      const e = err as BusinessError;
      console.error(`[VoiceService] Speak failed: ${e.code} - ${e.message}`);
      VoiceService.ttsStateCallback?.(false);
    }
  }

  /**
   * 停止当前 TTS 播放
   */
  static stopSpeaking(): void {
    if (VoiceService.ttsEngine) {
      try {
        VoiceService.ttsEngine.stop();
      } catch (err) {
        // ignore
      }
    }
  }

  // ==================== 清理 ====================

  static destroy(): void {
    VoiceService.stopListening();
    VoiceService.stopSpeaking();
    if (VoiceService.asrEngine) {
      VoiceService.asrEngine.shutdown();
      VoiceService.asrEngine = null;
      VoiceService.isAsrReady = false;
    }
    if (VoiceService.ttsEngine) {
      VoiceService.ttsEngine.shutdown();
      VoiceService.ttsEngine = null;
      VoiceService.isTtsReady = false;
    }
  }
}
