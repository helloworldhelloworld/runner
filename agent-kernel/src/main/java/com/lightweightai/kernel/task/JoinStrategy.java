package com.lightweightai.kernel.task;

/**
 * 汇聚策略
 *
 * 当一个任务依赖多个上游时，定义什么条件下该任务可以开始执行。
 */
public enum JoinStrategy {

    /** 所有上游都成功才执行（默认） */
    ALL_SUCCESS,

    /** 任一上游成功就执行 */
    ANY_SUCCESS,

    /** 等所有上游完成（不管成功失败）就执行 */
    ALL_COMPLETE,

    /** 任一上游完成就执行 */
    FIRST_COMPLETE
}
