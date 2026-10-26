package aurick.opsec.mod.mixin.client;

import aurick.opsec.mod.config.OpsecConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.options.ChatOptionsScreen;
import net.minecraft.client.gui.screens.options.OptionsSubScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;










@Mixin(ChatOptionsScreen.class)
public abstract class ChatOptionsScreenMixin extends OptionsSubScreen {

    protected ChatOptionsScreenMixin(Screen parent, net.minecraft.client.Options options, Component title) {
        super(parent, options, title);
    }

    


    @Override
    protected void init() {
        super.init();

        
        if (OpsecConfig.getInstance().shouldNotSign()) {
            
            Optional.ofNullable(this.list.findOption(Minecraft.getInstance().options.onlyShowSecureChat()))
                .ifPresent(option -> option.active = false);
        }
    }
}
