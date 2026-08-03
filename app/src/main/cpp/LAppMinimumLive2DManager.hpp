/**
 * Copyright(c) Live2D Inc. All rights reserved.
 *
 * Use of this source code is governed by the Live2D Open Software license
 * that can be found at https://www.live2d.com/eula/live2d-open-software-license-agreement_en.html.
 */

#pragma once

#include <CubismFramework.hpp>
#include <Math/CubismMatrix44.hpp>
#include <Type/csmVector.hpp>
#include <string>

class LAppMinimumModel;

/**
* @brief サンプルアプリケーションにおいてCubismModelを管理するクラス<br>
*         モデル生成と破棄、タップイベントの処理、モデル切り替えを行う。
*
*/
class LAppMinimumLive2DManager
{

public:
    /**
    * @brief   クラスのインスタンス（シングルトン）を返す。<br>
    *           インスタンスが生成されていない場合は内部でインスタンを生成する。
    *
    * @return  クラスのインスタンス
    */
    static LAppMinimumLive2DManager* GetInstance();

    /**
    * @brief   クラスのインスタンス（シングルトン）を解放する。
    *
    */
    static void ReleaseInstance();

    /**
    * @brief   現在のシーンで保持しているモデルを返す
    *
    * @return      モデルのインスタンスを返す。
    */
    LAppMinimumModel* GetModel() const;

    /**
    * @brief   モデルのオフスクリーンのサイズを設定
    *
    * @param[in]   width   ウインドウの幅
    * @param[in]   height  ウインドウの高さ
    */
    void SetRenderTargetSize(Csm::csmUint32 width, Csm::csmUint32 height);

    /**
    * @brief   現在のシーンで保持しているすべてのモデルを解放する
    *
    */
    void ReleaseModel();

    /**
    * @brief   画面をドラッグしたときの処理
    *
    * @param[in]   x   画面のX座標
    * @param[in]   y   画面のY座標
    */
    void OnDrag(Csm::csmFloat32 x, Csm::csmFloat32 y) const;

    /**
    * @brief   AIによるモーションと表情の制御
    *          指定されたモーショングループ・番号・表情を再生する
    *
    * @param[in]   motionGroup   モーショングループ名 ("Idle","Tap","TapHead","TapBody","Special")
    * @param[in]   motionNo      グループ内のモーション番号
    * @param[in]   expressionId  表情ID ("angry","cry","baozhen","qizi1","qizi2","white_eyes", 空文字の場合は表情変更なし)
    * @param[in]   priority      モーション優先度
    */
    void PerformAiAction(const Csm::csmChar* motionGroup, Csm::csmInt32 motionNo,
                         const Csm::csmChar* expressionId, Csm::csmInt32 priority);

    /**
    * @brief   画面を更新するときの処理
    *          モデルの更新処理および描画処理を行う
    */
    void OnUpdate() const;

private:
    /**
    * @brief  コンストラクタ
    */
    LAppMinimumLive2DManager();

    /**
    * @brief  デストラクタ
    */
    virtual ~LAppMinimumLive2DManager();

    /**
    * @brief ディレクトリパスの設定
    *
    * モデルのディレクトリパスを設定する
    */
    void SetAssetDirectory(const std::string& path);

    /**
    * @brief モデルの読み込み
    *
    * モデルデータの読み込み処理を行う
    *
    * @param[in] modelDirectory モデルのディレクトリ名
    */
    void LoadModel(const std::string modelDirectoryName);

    Csm::CubismMatrix44*        _viewMatrix; ///< モデル描画に用いるView行列
    LAppMinimumModel*  _model; ///< モデルインスタンス

    /**
    *@brief モデルデータのディレクトリ名
    * このディレクトリ名と同名の.model3.jsonを読み込む
    */
    std::string _modelDirectoryName;
    std::string _currentModelDirectory; ///< 現在のモデルのディレクトリ
};