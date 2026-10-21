package com.elfmcys.yesstevemodel.geckolib3.core.molang.value;

import com.elfmcys.yesstevemodel.molang.runtime.ExpressionEvaluator;

import java.util.concurrent.ConcurrentHashMap;

public class FloatValue implements IValue {

    public static final FloatValue ONE = new FloatValue(1.0f);

    public static final FloatValue ZERO = new FloatValue(0.0f);

    /**
     * 数值字面量驻留表。
     *
     * <p>实测 61 个模型里有 150 万+ 个 {@code FloatValue}，占 64 MB 堆——是 YSM
     * 侧最大的单一对象。而 Molang 表达式里的数值字面量高度重复：各模型都用
     * 0、1、0.5、-1 这类常量，每个模型各自 new 一份纯属浪费。
     *
     * <p>本类不可变（字段全 final），驻留完全安全。
     *
     * <p>容量设上限：浮点字面量理论上可以是任意值，无上限的缓存会变成内存泄漏。
     * 超过上限后不再新增条目，退化为普通分配。
     */
    private static final int MAX_INTERNED = 8192;
    private static final ConcurrentHashMap<Float, FloatValue> CACHE = new ConcurrentHashMap<>(256);

    private final float value;
    private final Float boxedValue;

    /**
     * 按值取驻留实例。
     *
     * <p>优先用它代替 {@code new FloatValue(x)}：同一数值在整个进程内只保留一份。
     */
    public static FloatValue of(float value) {
        // NaN 归一化到 0，与构造函数保持一致，避免 NaN 键污染缓存
        float normalized = Float.isNaN(value) ? 0.0f : value;
        // 正负零语义等价，统一成 +0.0，避免缓存里出现两份
        if (normalized == 0.0f) {
            return ZERO;
        }
        if (normalized == 1.0f) {
            return ONE;
        }
        Float key = normalized;
        FloatValue cached = CACHE.get(key);
        if (cached != null) {
            return cached;
        }
        FloatValue created = new FloatValue(normalized);
        if (CACHE.size() >= MAX_INTERNED) {
            return created;
        }
        FloatValue prev = CACHE.putIfAbsent(key, created);
        return prev != null ? prev : created;
    }

    public FloatValue(float value) {
        if (!Float.isNaN(value)) {
            this.value = value;
        } else {
            this.value = 0.0f;
        }
        this.boxedValue = this.value;
    }

    @Override
    public float evalAsFloat(ExpressionEvaluator<?> evaluator) {
        return this.value;
    }

    @Override
    public int evalAsInt(ExpressionEvaluator<?> evaluator) {
        return (int) this.value;
    }

    @Override
    public boolean evalAsBoolean(ExpressionEvaluator<?> evaluator) {
        return this.value != 0.0f;
    }

    @Override
    public Object evalSafe(ExpressionEvaluator<?> evaluator) {
        return this.boxedValue;
    }

    @Override
    public Object evalUnsafe(ExpressionEvaluator<?> evaluator) {
        return this.boxedValue;
    }

    public float value() {
        return this.value;
    }
}