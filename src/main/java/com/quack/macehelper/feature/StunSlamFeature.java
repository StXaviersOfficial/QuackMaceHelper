package com.quack.macehelper.feature;

import com.quack.macehelper.QuackMaceHelperClient;
import com.quack.macehelper.config.ModConfig;
import com.quack.macehelper.util.PlayerActionHelper;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;

import java.util.concurrent.ThreadLocalRandom;

public class StunSlamFeature {

    private enum Phase {
        IDLE,
        QUEUED_AFTER_ATTR_SWAP,
        GROUND_WAIT_HIT,
        GROUND_SWAP_BACK,
        FALLING_AXE_BURST,
        FALLING_MACE_BURST,
        FALLING_SWAP_BACK
    }

    private Phase phase = Phase.IDLE;
    private Entity activeTarget = null;
    private int savedSlot = -1;
    private int axeHitsRemaining = 0;
    private int maceHitsRemaining = 0;
    private int phaseTicksRemaining = 0;
    private boolean queuedFallingMode = false;

    public void onPlayerAttack(MinecraftClient client, ClientPlayerEntity player, Entity target, boolean attrSwapTriggeredSameHit) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch || !cfg.stunSlamEnabled) return;
        if (QuackMaceHelperClient.ALWAYS_ON_SHIELD.isTriggered()) return;
        if (!(target instanceof PlayerEntity)) return;

        // Constantly checks the player under crosshair; if shield NOT raised, do nothing
        if (!PlayerActionHelper.isShieldRaised(target)) return;

        // COLLISION RULE 5: if Shield Draining is enabled and the target's shield is up, Stun Slam must NOT run
        if (cfg.shieldDrainingEnabled) return;

        int axeSlot = PlayerActionHelper.findAxeInHotbar(player);
        if (axeSlot == -1) return;

        double heightDiff = PlayerActionHelper.getBodyHeightDifference(player, target);
        boolean fallingCase = heightDiff >= 3.0;
        if (fallingCase && PlayerActionHelper.findBestDamageMaceInHotbar(player, target) == -1) {
            return;
        }

        if (phase == Phase.IDLE) {
            savedSlot = player.getInventory().getSelectedSlot();
        }
        activeTarget = target;

        // COLLISION RULE 7: Attribute Swap owns the same-tick swap chain; Stun Slam queues behind it
        if (attrSwapTriggeredSameHit) {
            phase = Phase.QUEUED_AFTER_ATTR_SWAP;
            queuedFallingMode = fallingCase;
            return;
        }

        startSequence(client, player, target, axeSlot, fallingCase);
    }

    private void startSequence(MinecraftClient client, ClientPlayerEntity player, Entity target, int axeSlot, boolean fallingCase) {
        PlayerActionHelper.selectHotbarSlot(client, axeSlot);

        if (!fallingCase) {
            // Ground / jumping / under 3 blocks above target:
            // same tick switch to an axe, axe hit lands same tick OR next tick (random 0-1 tick delay), switch back
            int delay = ThreadLocalRandom.current().nextInt(0, 2); // 0 or 1
            if (delay == 0) {
                PlayerActionHelper.performRealAttack(client, target);
                phase = Phase.GROUND_SWAP_BACK;
                phaseTicksRemaining = 1;
            } else {
                phase = Phase.GROUND_WAIT_HIT;
                phaseTicksRemaining = 1;
            }
        } else {
            // 3+ blocks above target:
            // same tick switch to axe, land 4-5 axe hits within the next 0-1 ticks,
            // switch to best-damage mace, land 5-6 mace hits within the next 2-3 ticks,
            // then next tick switch back to original item
            int totalAxeHits = ThreadLocalRandom.current().nextInt(4, 6); // 4 or 5
            int axeTickSpan = ThreadLocalRandom.current().nextInt(0, 2); // 0 or 1
            if (axeTickSpan == 0) {
                for (int i = 0; i < totalAxeHits; i++) {
                    PlayerActionHelper.performRealAttack(client, target);
                }
                transitionToMaceBurst(client, player, target);
            } else {
                int nowHits = totalAxeHits / 2;
                for (int i = 0; i < nowHits; i++) {
                    PlayerActionHelper.performRealAttack(client, target);
                }
                axeHitsRemaining = totalAxeHits - nowHits;
                phase = Phase.FALLING_AXE_BURST;
                phaseTicksRemaining = 1;
            }
        }
    }

    private void transitionToMaceBurst(MinecraftClient client, ClientPlayerEntity player, Entity target) {
        maceHitsRemaining = ThreadLocalRandom.current().nextInt(5, 7); // 5 or 6
        phaseTicksRemaining = ThreadLocalRandom.current().nextInt(2, 4); // 2 or 3 ticks
        phase = Phase.FALLING_MACE_BURST;
    }

    public void tick(MinecraftClient client) {
        ModConfig cfg = ModConfig.get();
        if (!cfg.masterSwitch || !cfg.stunSlamEnabled || client.player == null) {
            reset();
            return;
        }
        if (QuackMaceHelperClient.ALWAYS_ON_SHIELD.isTriggered()) {
            reset();
            return;
        }
        if (cfg.shieldDrainingEnabled && activeTarget != null && PlayerActionHelper.isShieldRaised(activeTarget)) {
            reset();
            return;
        }

        ClientPlayerEntity player = client.player;

        if (phase == Phase.QUEUED_AFTER_ATTR_SWAP) {
            if (!QuackMaceHelperClient.ATTRIBUTE_SWAP.isChainActive()) {
                int axeSlot = PlayerActionHelper.findAxeInHotbar(player);
                if (axeSlot != -1 && activeTarget != null && activeTarget.isAlive()) {
                    startSequence(client, player, activeTarget, axeSlot, queuedFallingMode);
                } else {
                    reset();
                }
            }
            return;
        }

        if (phase == Phase.GROUND_WAIT_HIT) {
            int axeSlot = PlayerActionHelper.findAxeInHotbar(player);
            if (axeSlot != -1 && activeTarget != null && activeTarget.isAlive()) {
                PlayerActionHelper.selectHotbarSlot(client, axeSlot);
                PlayerActionHelper.performRealAttack(client, activeTarget);
            }
            phase = Phase.GROUND_SWAP_BACK;
            phaseTicksRemaining = 1;
            return;
        }

        if (phase == Phase.GROUND_SWAP_BACK || phase == Phase.FALLING_SWAP_BACK) {
            if (savedSlot >= 0 && savedSlot <= 8) {
                PlayerActionHelper.selectHotbarSlot(client, savedSlot);
            }
            reset();
            return;
        }

        if (phase == Phase.FALLING_AXE_BURST) {
            int axeSlot = PlayerActionHelper.findAxeInHotbar(player);
            if (axeSlot != -1 && activeTarget != null && activeTarget.isAlive()) {
                PlayerActionHelper.selectHotbarSlot(client, axeSlot);
                for (int i = 0; i < axeHitsRemaining; i++) {
                    PlayerActionHelper.performRealAttack(client, activeTarget);
                }
            }
            axeHitsRemaining = 0;
            transitionToMaceBurst(client, player, activeTarget);
            return;
        }

        if (phase == Phase.FALLING_MACE_BURST) {
            if (activeTarget == null || !activeTarget.isAlive()) {
                phase = Phase.FALLING_SWAP_BACK;
                return;
            }
            int maceSlot = PlayerActionHelper.findBestDamageMaceInHotbar(player, activeTarget);
            if (maceSlot == -1) {
                phase = Phase.FALLING_SWAP_BACK;
                return;
            }
            PlayerActionHelper.selectHotbarSlot(client, maceSlot);
            int hitsThisTick = (int) Math.ceil((double) maceHitsRemaining / Math.max(1, phaseTicksRemaining));
            for (int i = 0; i < hitsThisTick && maceHitsRemaining > 0; i++) {
                PlayerActionHelper.performRealAttack(client, activeTarget);
                maceHitsRemaining--;
            }
            phaseTicksRemaining--;
            if (maceHitsRemaining <= 0 || phaseTicksRemaining <= 0) {
                phase = Phase.FALLING_SWAP_BACK;
            }
        }
    }

    public void reset() {
        phase = Phase.IDLE;
        activeTarget = null;
        savedSlot = -1;
        axeHitsRemaining = 0;
        maceHitsRemaining = 0;
        phaseTicksRemaining = 0;
        queuedFallingMode = false;
    }
}
