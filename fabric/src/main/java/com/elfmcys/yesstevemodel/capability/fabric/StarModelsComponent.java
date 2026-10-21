package com.elfmcys.yesstevemodel.capability.fabric;

import com.elfmcys.yesstevemodel.capability.StarModelsCapability;
import com.mojang.serialization.Codec;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.ladysnake.cca.api.v3.component.Component;

public final class StarModelsComponent implements Component {

    private final StarModelsCapability capability = new StarModelsCapability();

    public StarModelsCapability capability() {
        return capability;
    }

    @Override
    public void readData(ValueInput input) {
        capability.clear();
        input.listOrEmpty("StarModels", Codec.STRING).stream().forEach(capability::addModel);
    }

    @Override
    public void writeData(ValueOutput output) {
        ValueOutput.TypedOutputList<String> list = output.list("StarModels", Codec.STRING);
        for (String s : capability.getStarModels()) {
            list.add(s);
        }
    }
}
