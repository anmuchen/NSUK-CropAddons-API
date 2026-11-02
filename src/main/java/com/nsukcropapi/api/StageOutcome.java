package com.nsukcropapi.api;

/**
 * 某个作业阶段一次执行的结果，对应 simukraft 内部的 {@code FarmlandWorkResult}。
 *
 * <p>宿主按结果决定雇农的下一步动作，所以三者语义必须严格区分：</p>
 */
public enum StageOutcome {

    /** 确实做成了事（翻了一格、种下一株、收到一次）。雇农会挥手，然后按工作间隔再来。 */
    PROCESSED,

    /** 这一格其实不用做（判断过期/位置不合法）。下一 tick 重新找目标，不显示缺料。 */
    SKIPPED,

    /** 只差材料（种子、土壤、骨粉、藤架……）。雇农头顶显示"等待种子：xxx"。 */
    WAITING_MATERIAL
}
