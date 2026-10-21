package com.elfmcys.yesstevemodel.geckolib3.resource;

import com.elfmcys.yesstevemodel.client.animation.molang.TLMBinding;
import com.elfmcys.yesstevemodel.client.animation.molang.YSMBinding;
import com.elfmcys.yesstevemodel.client.animation.molang.ArgsVariable;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.MolangParser;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin.MathBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.builtin.QueryBinding;
import com.elfmcys.yesstevemodel.geckolib3.core.molang.value.IValue;
import com.elfmcys.yesstevemodel.client.animation.molang.CtrlBinding;
import com.elfmcys.yesstevemodel.client.animation.molang.FnBinding;
import com.elfmcys.yesstevemodel.molang.parser.ParseException;
import org.apache.commons.lang3.concurrent.ConcurrentException;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.regex.Pattern;

public class GeckoLibCache {

    private static final ConcurrentLinkedQueue<MolangParser> PARSER_POOL = new ConcurrentLinkedQueue<>();

    private static final Map<String, Object> EXTRA_BINDING = new HashMap<>();

    private static final Map<String, Object> bindings = new HashMap<>();

    private static final Pattern ROAMING_VAR_PATTERN = Pattern.compile("^([;\\s]*(v|variable)\\.roaming\\.[A-Za-z0-9_]+\\s*=[^;]+[;\\s]*)+$", 2);

    public static MolangParser getMolangParser() {
        MolangParser parser = PARSER_POOL.poll();
        if (parser == null) {
            return createMolangParser();
        }
        return parser;
    }

    public static void releaseParser(MolangParser parser) {
        parser.reset();
        PARSER_POOL.add(parser);
    }

    public static IValue parseSimpleExpression(String molangExpression) throws ParseException {
        MolangParser parser = getMolangParser();
        try {
            return parser.parseExpressionUnsafe(molangExpression, false);
        } finally {
            releaseParser(parser);
        }
    }

    /**
     * 惰性初始化锁。
     *
     * <p>{@link #EXTRA_BINDING} 与 {@link #bindings} 是普通 {@code HashMap}，
     * 原来的 {@code if (isEmpty()) { put(...) }} 写法在多线程下会同时进入，
     * 并发写同一个 HashMap 会破坏其内部结构（丢条目甚至死循环）。
     *
     * <p>模型解析已经改为并行（多线程调用 {@code buildParsedBundle} →
     * {@code YSMClientMapper.parse} → 本类的 {@code getMolangParser}），
     * 因此这里必须加锁。缺了锁会导致 {@code ysm}/{@code ctrl}/{@code fn}
     * 等绑定丢失，Molang 表达式解析失败后退化为 {@code FloatValue.ZERO}，
     * 表现为动画全部失效、轮盘和玩家预览空白。
     */
    private static final Object BINDING_INIT_LOCK = new Object();

    private static void ensureExtraBindings() {
        if (!EXTRA_BINDING.isEmpty()) {
            return;
        }
        synchronized (BINDING_INIT_LOCK) {
            if (!EXTRA_BINDING.isEmpty()) {
                return;
            }
            try {
                EXTRA_BINDING.put("ysm", YSMBinding.INSTANCE.get());
                EXTRA_BINDING.put("ctrl", CtrlBinding.INSTANCE.get());
                EXTRA_BINDING.put("tlm", TLMBinding.INSTANCE.get());
                EXTRA_BINDING.put("args", ArgsVariable.INSTANCE);
            } catch (ConcurrentException e) {
                throw new RuntimeException(e);
            }
        }
    }

    private static MolangParser createMolangParser() {
        ensureExtraBindings();
        // 在锁内拷贝：EXTRA_BINDING 初始化完成后不再变动，但拷贝动作本身
        // 与初始化并发时仍可能读到半成品，用同一把锁保证可见性。
        HashMap<String, Object> map;
        synchronized (BINDING_INIT_LOCK) {
            map = new HashMap<>(EXTRA_BINDING);
        }
        map.put("fn", new FnBinding());
        return new MolangParser(map);
    }

    public static Map<String, Object> getGlobalBindings() {
        if (bindings.isEmpty()) {
            ensureExtraBindings();
            synchronized (BINDING_INIT_LOCK) {
                if (bindings.isEmpty()) {
                    bindings.putAll(EXTRA_BINDING);
                    bindings.put("math", MathBinding.INSTANCE);
                    bindings.put("q", QueryBinding.INSTANCE);
                }
            }
        }
        return bindings;
    }

    public static boolean isRoamingVariableAssignment(String str) {
        return ROAMING_VAR_PATTERN.matcher(str).find();
    }
}