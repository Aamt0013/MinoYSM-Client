package com.elfmcys.yesstevemodel.capability.fabric;

import com.elfmcys.yesstevemodel.capability.AuthModelsCapability;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.ladysnake.cca.api.v3.component.Component;

public final class AuthModelsComponent implements Component {

    private final AuthModelsCapability capability = new AuthModelsCapability();

    public AuthModelsCapability capability() {
        return capability;
    }

    @Override
    public void readData(ValueInput input) {
        capability.clear();
        input.listOrEmpty("AuthModels", Codec.STRING).stream().forEach(capability::addModel);
    }

    @Override
    public void writeData(ValueOutput output) {
        ValueOutput.TypedOutputList<String> list = output.list("AuthModels", Codec.STRING);
        for (String s : capability.getAuthModels()) {
            list.add(s);
        }
    }
}
