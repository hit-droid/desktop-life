/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#include "LAppDefine.hpp"
#include <CubismFramework.hpp>

namespace LAppDefine {

    using namespace Csm;

    // 画面
    const csmFloat32 ViewScale = 1.0f;
    const csmFloat32 ViewMaxScale = 2.0f;
    const csmFloat32 ViewMinScale = 0.8f;

    const csmFloat32 ViewLogicalLeft = -1.0f;
    const csmFloat32 ViewLogicalRight = 1.0f;
    const csmFloat32 ViewLogicalBottom = -1.0f;
    const csmFloat32 ViewLogicalTop = 1.0f;

    const csmFloat32 ViewLogicalMaxLeft = -2.0f;
    const csmFloat32 ViewLogicalMaxRight = 2.0f;
    const csmFloat32 ViewLogicalMaxBottom = -2.0f;
    const csmFloat32 ViewLogicalMaxTop = 2.0f;

    // 相対パス - 模型在assets中的路径
    const csmChar* ResourcesPath = "web/models/";

    // シェーダー相対パス
    const csmChar* ShaderPath = "web/";
    // 頂点シェーダー
    const csmChar* VertShaderName = "VertSprite.vert";
    // フラグメントシェーダー
    const csmChar* FragShaderName = "FragSprite.frag";

    // モデル定義------------------------------------------
    // 模型在assets中的目录名（与model3.json名称一致）
    const csmChar* ModelDir[] = {
        "huohuo"
    };
    const csmInt32 ModelDirSize = sizeof(ModelDir) / sizeof(const csmChar*);

    // 外部定義ファイル(json)と合わせる
    const csmChar* MotionGroupIdle = "Idle"; // アイドリング
    const csmChar* MotionGroupTapBody = "TapBody"; // 体をタップしたとき

    // 外部定義ファイル(json)と合わせる - 注意：需与model3.json中的HitAreas名称匹配
    const csmChar* HitAreaNameHead = "head";
    const csmChar* HitAreaNameBody = "body";

    // モーションの優先度定数
    const csmInt32 PriorityNone = 0;
    const csmInt32 PriorityIdle = 1;
    const csmInt32 PriorityNormal = 2;
    const csmInt32 PriorityForce = 3;

    // デバッグ用ログの表示オプション
    const csmBool DebugLogEnable = true;
    const csmBool DebugTouchLogEnable = false;

    // Frameworkから出力するログのレベル設定
    const CubismFramework::Option::LogLevel CubismLoggingLevel = CubismFramework::Option::LogLevel_Verbose;
}