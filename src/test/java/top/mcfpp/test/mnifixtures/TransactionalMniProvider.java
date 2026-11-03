package top.mcfpp.test.mnifixtures;

import top.mcfpp.Project;
import top.mcfpp.analysis.StorageAccess;
import top.mcfpp.command.Command;
import top.mcfpp.core.lang.MCInt;
import top.mcfpp.core.lang.Var;
import top.mcfpp.lib.SbObject;
import top.mcfpp.model.Namespace;
import top.mcfpp.model.function.Function;
import top.mcfpp.model.scope.GlobalScope;
import top.mcfpp.util.LogProcessor;
import top.mcfpp.util.TempPool;
import top.mcfpp.util.ValueWrapper;

/**
 * Real legacy MNI host used by {@code MniCompilerBridgeIntegrationTest}.
 *
 * <p>The entry mutates every compiler domain that the production transaction owns:
 * commands, the current function scope, a published function/helper, resources
 * (an objective and a load-time storage write), the call graph, generated names,
 * storage/flow facts and diagnostics. It performs that multi-domain write and then
 * either throws or publishes its result. The bridge must observe the partial write
 * and restore the exact before state when the host fails.</p>
 */
public final class TransactionalMniProvider {
    public static int calls;
    public static boolean failNext = true;

    public static int commandWrites;
    public static int scopeWrites;
    public static int functionWrites;
    public static int resourceWrites;
    public static int callGraphWrites;
    public static int nameWrites;
    public static int flowWrites;
    public static int diagnosticWrites;

    private TransactionalMniProvider() {}

    public static void reset() {
        calls = 0;
        failNext = true;
        commandWrites = 0;
        scopeWrites = 0;
        functionWrites = 0;
        resourceWrites = 0;
        callGraphWrites = 0;
        nameWrites = 0;
        flowWrites = 0;
        diagnosticWrites = 0;
    }

    public static void mutate(ValueWrapper<MCInt> result) {
        calls++;

        // The generated plan block is the current function. Its first parent is the
        // original source caller whose compiler state must be restored.
        Function caller = Function.Companion.getCurrFunction();
        while (!caller.getParent().isEmpty()) {
            caller = caller.getParent().get(0);
        }
        Namespace namespace = GlobalScope.INSTANCE.getLocalNamespaces().get(caller.getNamespace());
        if (namespace == null) {
            throw new IllegalStateException("transactional fixture has no published caller namespace");
        }

        // COMMANDS: a direct write to the original caller command list.
        int commandCount = caller.getCommands().size();
        caller.getCommands().add(new Command("say transactional-command"));
        if (caller.getCommands().size() == commandCount + 1) {
            commandWrites++;
        }

        // SCOPES: a real function-scope declaration.
        MCInt scopeMarker = new MCInt(41, "txnScopeMarker");
        scopeMarker.bindDeclaration("txnScopeMarker", null, false);
        caller.getScope().getVars().put("txnScopeMarker", scopeMarker);
        if (caller.getScope().getVars().get("txnScopeMarker") == scopeMarker) {
            scopeWrites++;
        }

        // FUNCTIONS / helpers: publish a real function in the caller namespace.
        Function helper = new Function("txnHelper", caller.getNamespace(), null);
        if (!namespace.getScope().addFunction(helper, false)) {
            throw new IllegalStateException("transactional fixture could not publish its helper");
        }
        functionWrites++;

        // RESOURCES: a real objective plus a real load-time storage command.
        new SbObject("txn_resource", "dummy", null);
        resourceWrites++;
        Project.INSTANCE.getProjectLoad().getCommands()
                .add(new Command("data modify storage bridge:fixture set value {count:1}"));
        resourceWrites++;

        // CALL_GRAPH: real caller/callee edges around the published helper.
        caller.getChild().add(helper);
        helper.getParent().add(caller);
        callGraphWrites += 2;

        // NAMES: a macro resource name and the shared temporary-name allocator.
        Project.INSTANCE.getMacroFunction().put("bridge/transactional", "$say transactional");
        nameWrites++;
        TempPool.INSTANCE.setFunctionCount(TempPool.INSTANCE.getFunctionCount() + 1);
        nameWrites++;

        // FLOW: write an enrolled storage variable so facts and write versions change.
        Var<?> flow = Project.INSTANCE.getConstants().get("bridge-transaction-flow");
        if (flow == null) {
            throw new IllegalStateException("transactional fixture lost its flow target");
        }
        StorageAccess.INSTANCE.write(flow, new MCInt(99, "txnFlowWrite"));
        flowWrites++;

        // DIAGNOSTICS: a real compiler warning in the journaled diagnostic domain.
        LogProcessor.warn("transactional MNI partial diagnostic");
        diagnosticWrites++;

        if (failNext) {
            throw new IllegalStateException("transactional legacy MNI failure");
        }
        result.set(new MCInt(7, "transactional-result"));
    }
}
