package org.interpss.fadapter.psse;

import java.util.List;
import java.util.stream.Collectors;

import com.interpss.dstab.BaseDStabNetwork;

/**
 * Imported governor travel policy (an adapter {@code .bsg} file) registered on a
 * solver and replayed on the final startup storage of the governors. Used by the
 * adapter solvers through the Core solver's governor-policy hooks.
 */
public final class GovernorPolicyReplay {
    private List<PsseGovernorBsgProcessor.Entry> policy = List.of();
    private List<PsseGovernorBsgProcessor.Entry> report = List.of();

    /** Registration is inert; numerical replay occurs only after final startup preparation. */
    public void register(BaseDStabNetwork<?, ?> network, List<PsseGovernorBsgProcessor.Entry> entries) {
        if (network.isDStabNetInitialized())
            throw new IllegalStateException("Register governor policy before initialization");
        policy = List.copyOf(entries);
        report = policy;
    }

    public List<PsseGovernorBsgProcessor.Entry> report() { return report; }

    public void reset() { report = policy; }

    /** Apply the policy; false when replay failed or an entry reports an error. */
    public boolean apply(BaseDStabNetwork<?, ?> network, Class<?> owner) {
        if (policy.isEmpty()) return true;
        var log = org.slf4j.LoggerFactory.getLogger(owner);
        try { report = PsseGovernorBsgProcessor.apply(network, policy); }
        catch (com.interpss.common.exp.InterpssException failure) {
            log.error("Governor policy replay failed", failure);
            return false;
        }
        var counts = report.stream().collect(Collectors.groupingBy(
                PsseGovernorBsgProcessor.Entry::status, Collectors.counting()));
        log.info("Initial governor policy outcomes: {}; inspect getGovernorBlockingReport() for source/old/new limits", counts);
        return !counts.containsKey(PsseGovernorBsgProcessor.Status.ERROR);
    }
}
