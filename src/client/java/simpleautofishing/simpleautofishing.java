package simpleautofishing;


import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;

import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import simpleautofishing.mixin.FishingBobberEntityAccessorMixin;
import net.minecraft.util.Hand;
import net.minecraft.client.MinecraftClient;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.api.ClientModInitializer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class simpleautofishing implements ClientModInitializer {
	private static MinecraftClient client;
	public static final Logger LOGGER = LoggerFactory.getLogger("simpleautofishing");
	FishingRodModes FishingRodMode = FishingRodModes.fishingRodUnprotected;
	int delay = 0;
	public static int recastDelayTicks = 17;
	boolean reeledIn, stateAttackKeyReleased = false;
	enum FishingRodModes {
		fishingRodUnprotected,
		fishingRodProtected,
		allInHotbar,
		allInHotbarProtected;

		public FishingRodModes next() {
			return values()[(ordinal() + 1) % values().length];
		}
	};

	@Override
	public void onInitializeClient() {
		LOGGER.info("Registering simpleautofishing!");
		ClientTickEvents.START_CLIENT_TICK.register(this::onTick);

		ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> {
			dispatcher.register(ClientCommandManager.literal("saf")
					.then(ClientCommandManager.literal("set")
							.then(ClientCommandManager.argument("delay", IntegerArgumentType.integer())
									.executes(context -> {
										recastDelayTicks = IntegerArgumentType.getInteger(context, "delay");
										context.getSource().sendFeedback(Text.translatable("text.simpleautofishing.cmd.recastDelayTicks", recastDelayTicks));
										return 1;
									})
							)
					)
			);
		});
	}

	private void onTick(MinecraftClient _client) {
		if (MinecraftClient.getInstance() == null) {
			return;
		} else if (MinecraftClient.getInstance() != null && client == null) {
			client = MinecraftClient.getInstance();
		}

		if (client.player == null) {
			return;
		}

		if (!isFishingRodEquipped()) {
			delay = 0;
			reeledIn = false;
			return;
		}

		if (client.player.isSneaking() && attackKeyReleased(client.options.attackKey.isPressed())) {
			FishingRodMode = FishingRodMode.next();
			if (FishingRodMode == FishingRodModes.fishingRodUnprotected) {
				client.player.sendMessage(Text.translatable("text.simpleautofishing.safMode.fishing_rod_unprotected"), true);
			} else if (FishingRodMode == FishingRodModes.fishingRodProtected) {
				client.player.sendMessage(Text.translatable("text.simpleautofishing.safMode.fishing_rod_protected"), true);
			} else if (FishingRodMode == FishingRodModes.allInHotbar) {
				client.player.sendMessage(Text.translatable("text.simpleautofishing.safMode.all_in_hotbar"), true);
			} else if (FishingRodMode == FishingRodModes.allInHotbarProtected) {
				client.player.sendMessage(Text.translatable("text.simpleautofishing.safMode.all_in_hotbar_protected"), true);
			}
		}

		if (client.player.fishHook != null && caughtFish(((FishingBobberEntityAccessorMixin) client.player.fishHook).getCaughtFish())) {
			useRod();
			reeledIn = true;
			delay = 0;
		}

		if (!reeledIn) {
			return;
		}

		if (delay > recastDelayTicks) {
			useRod();
			reeledIn = false;
			delay = 0;
		} else {
			delay++;
		}
	}

	public void useRod() {
		switch (FishingRodMode) {
			case FishingRodModes.fishingRodUnprotected:
				client.player.swingHand(Hand.MAIN_HAND);
				client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				break;
			case FishingRodModes.fishingRodProtected:
				if (client.player.getMainHandStack().getDamage() <= client.player.getMainHandStack().getMaxDamage() - 4) {
					client.player.swingHand(Hand.MAIN_HAND);
					client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				}
				break;
			case FishingRodModes.allInHotbar:
				client.player.swingHand(Hand.MAIN_HAND);
				client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				if (reeledIn) {
					break;
				}
				if (isFishingRodEquipped() && client.player.getMainHandStack().getDamage() + 1 != client.player.getMainHandStack().getMaxDamage()) {
					break;
				}
				int currentSlot = client.player.getInventory().getSelectedSlot();
				for (int i = 0; i < 9; i++) {
					if (isFishingRodEquipped(client.player.getInventory().getStack(i)) && currentSlot != i) {
						client.player.getInventory().setSelectedSlot(i);
						break;
					}
				}
			case FishingRodModes.allInHotbarProtected:
				if (client.player.getMainHandStack().getDamage() > client.player.getMainHandStack().getMaxDamage() - 4) {
					int currentSlotProtected = client.player.getInventory().getSelectedSlot();
					boolean switched = false;
					for (int i = 0; i < 9; i++) {
						ItemStack stack = client.player.getInventory().getStack(i);
						if (isFishingRodEquipped(stack) && currentSlotProtected != i
								&& stack.getDamage() <= stack.getMaxDamage() - 4) {
							client.player.getInventory().setSelectedSlot(i);
							switched = true;
							break;
						}
					}
					if (!switched) {
						break;
					}
				}
				client.player.swingHand(Hand.MAIN_HAND);
				client.interactionManager.interactItem(client.player, Hand.MAIN_HAND);
				if (reeledIn) {
					break;
				}
				if (isFishingRodEquipped() && client.player.getMainHandStack().getDamage() + 1 != client.player.getMainHandStack().getMaxDamage()) {
					break;
				}
				int currentSlotSwitch = client.player.getInventory().getSelectedSlot();
				for (int i = 0; i < 9; i++) {
					ItemStack stack = client.player.getInventory().getStack(i);
					if (isFishingRodEquipped(stack) && currentSlotSwitch != i
							&& stack.getDamage() <= stack.getMaxDamage() - 4) {
						client.player.getInventory().setSelectedSlot(i);
						break;
					}
				}
				break;
		}
	}

	public static boolean isFishingRodEquipped() {
		if (client.getInstance().player.getMainHandStack().isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "tools/fishing_rod")))) {
			return true;
		} else if (client.getInstance().player.getMainHandStack().getItem() == Items.FISHING_ROD) {
			return true;
		} else {
			return false;
		}
	}

	public static boolean isFishingRodEquipped(ItemStack stack) {
		if (stack.isIn(TagKey.of(RegistryKeys.ITEM, Identifier.of("c", "tools/fishing_rod")))) {
			return true;
		} else if (stack.getItem() == Items.FISHING_ROD) {
			return true;
		} else {
			return false;
		}
	}

	public boolean attackKeyReleased(boolean currentState) {
		boolean fallingEdge = stateAttackKeyReleased && !currentState;
		stateAttackKeyReleased = currentState;
		return fallingEdge;
	}

	public boolean caughtFish(boolean currentState) {
		boolean risingEdge = !stateAttackKeyReleased && currentState;
		stateAttackKeyReleased = currentState;
		return risingEdge;
	}
}