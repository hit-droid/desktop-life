package com.desktop.life

import android.os.Handler
import android.os.Looper
import android.util.Log
import kotlin.random.Random

/**
 * AI动作控制器 - 将AI对话文本解析为Live2D角色动作与表情细节
 *
 * 功能：
 * 1. 解析AI回复文本中的情感/意图
 * 2. 映射到对应的Live2D动作组和表情
 * 3. 控制参数级细节（头部倾斜、眼睛运动、身体晃动）
 * 4. 支持动作序列编排
 */
class AiActionController {

    companion object {
        private const val TAG = "AiActionController"

        // 动作优先级（与LAppDefine对应）
        const val PRIORITY_NONE = 0
        const val PRIORITY_IDLE = 1
        const val PRIORITY_NORMAL = 2
        const val PRIORITY_FORCE = 3

        // 可用动作组
        const val GROUP_IDLE = "Idle"
        const val GROUP_TAP = "Tap"
        const val GROUP_TAP_HEAD = "TapHead"
        const val GROUP_TAP_BODY = "TapBody"
        const val GROUP_SPECIAL = "Special"

        // 可用表情
        const val EXPR_ANGRY = "angry"
        const val EXPR_BAOZHEN = "baozhen"
        const val EXPR_CRY = "cry"
        const val EXPR_QIZI1 = "qizi1"
        const val EXPR_QIZI2 = "qizi2"
        const val EXPR_WHITE_EYES = "white_eyes"
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var isAnimating = false
    private var actionQueue = mutableListOf<AiAction>()

    // ==================== 动作模型 ====================

    data class AiAction(
        val motionGroup: String = "",
        val motionNo: Int = 0,
        val expressionId: String = "",
        val priority: Int = PRIORITY_NORMAL,
        val delayMs: Long = 0L,          // 延迟执行时间
        val durationMs: Long = 0L,       // 持续时长（0为不限制）
        val params: Map<String, Float> = emptyMap() // 参数级控制
    )

    // ==================== 情感→动作映射 ====================

    private val emotionMappings = listOf(
        EmotionMapping(
            keywords = listOf("开心", "高兴", "哈哈", "嘻嘻", "真好", "喜欢", "爱", "好棒", "嘿嘿", "笑", "开心死了", "太棒了", "快乐"),
            action = AiAction(
                motionGroup = GROUP_TAP,
                motionNo = 0,      // haoqi.motion3 - 好奇/开心
                expressionId = EXPR_WHITE_EYES,
                priority = PRIORITY_NORMAL
            ),
            secondaryAction = AiAction(
                motionGroup = GROUP_TAP,
                motionNo = 1,      // yaotou.motion3 - 摇头晃脑
                expressionId = "",
                priority = PRIORITY_IDLE,
                delayMs = 800
            )
        ),
        EmotionMapping(
            keywords = listOf("难过", "伤心", "哭", "呜呜", "哭了", "不开心", "可怜", "悲伤", "心痛", "难受", "委屈"),
            action = AiAction(
                motionGroup = GROUP_IDLE,
                motionNo = 1,      // yaotou.motion3 - 轻轻摇头
                expressionId = EXPR_CRY,
                priority = PRIORITY_NORMAL
            )
        ),
        EmotionMapping(
            keywords = listOf("困", "睡觉", "晚安", "好累", "疲惫", "瞌睡", "zzz", "累了", "休息", "困死了"),
            action = AiAction(
                motionGroup = GROUP_IDLE,
                motionNo = 2,      // keshui.motion3 - 瞌睡
                expressionId = "",
                priority = PRIORITY_NORMAL
            )
        ),
        EmotionMapping(
            keywords = listOf("生气", "愤怒", "气死", "讨厌", "可恶", "烦", "烦死了", "哼", "怒", "气人"),
            action = AiAction(
                motionGroup = GROUP_SPECIAL,
                motionNo = 1,      // qizi.motion3 - 生气
                expressionId = EXPR_ANGRY,
                priority = PRIORITY_FORCE
            )
        ),
        EmotionMapping(
            keywords = listOf("惊讶", "哇", "真的吗", "不会吧", "天哪", "震惊", "吃惊", "吓", "什么", "诶"),
            action = AiAction(
                motionGroup = GROUP_SPECIAL,
                motionNo = 0,      // linghun.motion3 - 灵魂出窍/震惊
                expressionId = EXPR_BAOZHEN,
                priority = PRIORITY_FORCE
            )
        ),
        EmotionMapping(
            keywords = listOf("害羞", "不好意思", "羞", "脸红", "尴尬", "难为情", "讨厌啦"),
            action = AiAction(
                motionGroup = GROUP_TAP_HEAD,
                motionNo = 0,      // zhentou.motion3 - 摸头/害羞
                expressionId = EXPR_QIZI1,
                priority = PRIORITY_NORMAL
            )
        ),
        EmotionMapping(
            keywords = listOf("亲", "抱抱", "摸摸", "蹭", "贴贴", "撒娇", "乖", "可爱", "么么", "喜欢你"),
            action = AiAction(
                motionGroup = GROUP_TAP_HEAD,
                motionNo = 0,      // zhentou.motion3 - 蹭蹭
                expressionId = EXPR_QIZI2,
                priority = PRIORITY_NORMAL
            )
        ),
        EmotionMapping(
            keywords = listOf("无聊", "没意思", "好闲", "干啥", "干嘛", "什么"),
            action = AiAction(
                motionGroup = GROUP_IDLE,
                motionNo = 0,      // Scene1.motion3 - 待机四处看
                expressionId = EXPR_WHITE_EYES,
                priority = PRIORITY_IDLE
            )
        ),
        // 默认：开心好奇
        EmotionMapping(
            keywords = listOf(),  // 空关键词作为默认匹配
            action = AiAction(
                motionGroup = GROUP_TAP_BODY,
                motionNo = 0,      // haoqi.motion3 - 好奇
                expressionId = "",
                priority = PRIORITY_NORMAL
            )
        )
    )

    data class EmotionMapping(
        val keywords: List<String>,
        val action: AiAction,
        val secondaryAction: AiAction? = null
    )

    // ==================== 参数级细节控制 ====================

    /** 说话时的参数动画（嘴部、头部微动） */
    fun createSpeakingParams(): Map<String, Float> = mapOf(
        "ParamMouthOpenY" to 0.5f,   // 微微张嘴
        "ParamAngleZ" to 2f,         // 轻微歪头
        "ParamEyeBallY" to -0.3f     // 视线向下看（看输入框）
    )

    /** 思考时的参数动画 */
    fun createThinkingParams(): Map<String, Float> = mapOf(
        "ParamAngleX" to 5f,         // 歪头思考
        "ParamEyeBallX" to 0.5f,     // 眼球向右上看
        "ParamEyeBallY" to 0.3f,
        "ParamMouthOpenY" to 0.2f    // 微张嘴
    )

    /** 聆听时的参数动画 */
    fun createListeningParams(): Map<String, Float> = mapOf(
        "ParamAngleX" to 3f,         // 微微侧头
        "ParamEyeBallX" to 0.4f,     // 看向说话者方向
        "ParamEyeBallY" to 0.1f,
        "ParamBodyAngleX" to 2f,     // 身体微微前倾
        "ParamMouthOpenY" to 0.1f
    )

    /** 开心时的参数微调 */
    fun createHappyParams(): Map<String, Float> = mapOf(
        "ParamAngleZ" to -3f,        // 头向右歪
        "ParamEyeBallX" to 0.2f,
        "ParamEyeBallY" to 0.3f,     // 眼睛向上弯（笑眼）
        "ParamBodyAngleX" to -2f,    // 身体微微晃动
        "ParamMouthOpenY" to 0.3f
    )

    // ==================== 核心API ====================

    /**
     * 根据AI回复文本，解析情感并触发相应的动作序列
     */
    fun performActionForResponse(aiText: String) {
        Log.d(TAG, "Parsing AI response for action: $aiText")

        // 1. 检测情感
        val mapping = detectEmotion(aiText)

        // 2. 先播放表情
        if (mapping.action.expressionId.isNotEmpty()) {
            JniBridgeJava.nativeSetExpression(mapping.action.expressionId)
        }

        // 3. 播放主动作
        if (mapping.action.motionGroup.isNotEmpty()) {
            JniBridgeJava.nativeStartMotion(
                mapping.action.motionGroup,
                mapping.action.motionNo,
                mapping.action.priority
            )
        }

        // 4. 延迟播放副动作（如果有）
        mapping.secondaryAction?.let { secondary ->
            if (secondary.delayMs > 0) {
                mainHandler.postDelayed({
                    if (secondary.motionGroup.isNotEmpty()) {
                        JniBridgeJava.nativeStartMotion(
                            secondary.motionGroup,
                            secondary.motionNo,
                            secondary.priority
                        )
                    }
                }, secondary.delayMs)
            }
        }

        // 5. 应用参数级细节（嘴部、头部微动）
        applyDetailParams(aiText)
    }

    /**
     * 播放思考中的动作（AI正在生成回复时）
     */
    fun performThinkingAction() {
        Log.d(TAG, "Playing thinking action")

        // 细微的表情变化 + 轻微歪头思考
        JniBridgeJava.nativeSetExpression(EXPR_WHITE_EYES)

        // 播放轻声好奇的待机动作
        JniBridgeJava.nativeStartMotion(GROUP_TAP, 0, PRIORITY_NORMAL)
    }

    /**
     * 播放聆听状态（用户说话/输入时）
     */
    fun performListeningAction() {
        Log.d(TAG, "Playing listening action")

        // 好奇表情 + 身体前倾
        JniBridgeJava.nativeSetExpression(EXPR_WHITE_EYES)

        // 播放好奇动作
        JniBridgeJava.nativeStartMotion(GROUP_TAP_BODY, 0, PRIORITY_NORMAL)
    }

    /**
     * 播放空闲状态下的随机小动作
     */
    fun performRandomIdleAction() {
        if (isAnimating) return

        val idleActions = listOf(
            AiAction(GROUP_IDLE, 0, EXPR_WHITE_EYES, PRIORITY_IDLE),      // 四处看
            AiAction(GROUP_IDLE, 1, "", PRIORITY_IDLE),                    // 轻轻摇头
            AiAction(GROUP_TAP, 0, "", PRIORITY_IDLE),                     // 好奇
            AiAction(GROUP_TAP_BODY, 0, "", PRIORITY_IDLE),                // 身体动作
            AiAction(GROUP_TAP_HEAD, 0, EXPR_QIZI1, PRIORITY_IDLE),        // 害羞摸头
        )

        val action = idleActions.random()
        if (action.expressionId.isNotEmpty()) {
            JniBridgeJava.nativeSetExpression(action.expressionId)
        }
        JniBridgeJava.nativeStartMotion(action.motionGroup, action.motionNo, action.priority)
    }

    /**
     * 播放角色问候/打招呼
     */
    fun performGreetingAction() {
        val greetings = listOf(
            AiAction(GROUP_TAP, 0, EXPR_WHITE_EYES, PRIORITY_NORMAL),
            AiAction(GROUP_TAP_BODY, 0, EXPR_QIZI2, PRIORITY_NORMAL),
            AiAction(GROUP_TAP_HEAD, 0, EXPR_WHITE_EYES, PRIORITY_NORMAL),
        )
        val action = greetings.random()
        JniBridgeJava.nativePerformAiAction(
            action.motionGroup, action.motionNo,
            action.expressionId, action.priority
        )
    }

    // ==================== 情感检测 ====================

    private fun detectEmotion(text: String): EmotionMapping {
        // 先按关键词匹配
        for (mapping in emotionMappings) {
            if (mapping.keywords.isEmpty()) continue // 跳过默认
            if (mapping.keywords.any { text.contains(it) }) {
                Log.d(TAG, "Detected emotion via keywords: ${mapping.keywords.first()}")
                return mapping
            }
        }
        // 无匹配时使用默认动作
        Log.d(TAG, "No emotion detected, using default")
        return emotionMappings.last() // 默认
    }

    /**
     * 根据回复文本长度和情感，应用参数级细节
     */
    private fun applyDetailParams(aiText: String) {
        // 长回复时播放更多细节动作
        if (aiText.length > 20) {
            // 在回复较长时，延时播放一个额外的微动作
            mainHandler.postDelayed({
                JniBridgeJava.nativeStartMotion(GROUP_TAP, 1, PRIORITY_IDLE)
            }, 1500)
        }
    }

    // ==================== 动作序列 ====================

    /**
     * 执行一个自定义动作序列
     * 可用于AI在对话过程中编排复杂的动作组合
     */
    fun executeSequence(actions: List<AiAction>) {
        var totalDelay = 0L
        for (action in actions) {
            totalDelay += action.delayMs
            mainHandler.postDelayed({
                JniBridgeJava.nativePerformAiAction(
                    action.motionGroup, action.motionNo,
                    action.expressionId, action.priority
                )
            }, totalDelay)
        }
    }

    /**
     * 重置角色到空闲状态
     */
    fun resetToIdle() {
        JniBridgeJava.nativePerformAiAction(
            GROUP_IDLE, 0, "", PRIORITY_IDLE
        )
    }

    fun destroy() {
        mainHandler.removeCallbacksAndMessages(null)
    }
}