package top.mcfpp.mni.minecraft;

import top.mcfpp.annotations.MNIFunction;
import top.mcfpp.core.minecraft.PlayerInventory;

public class PlayerInventoryData {

    private PlayerInventoryData(){}

    @MNIFunction(caller = "PlayerInventory")
    public static void clear(PlayerInventory caller){
        var player = caller.getPlayer();
    }

}
