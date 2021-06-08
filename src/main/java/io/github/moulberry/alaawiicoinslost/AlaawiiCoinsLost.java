package io.github.moulberry.alaawiicoinslost;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.command.ICommandSender;
import net.minecraft.util.ChatComponentText;
import net.minecraftforge.client.ClientCommandHandler;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.io.File;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Mod(modid = AlaawiiCoinsLost.MODID, version = AlaawiiCoinsLost.VERSION, clientSideOnly = true)
public class AlaawiiCoinsLost {

    public static final String MODID = "alawiicoinslost";
    public static final String VERSION = "6.9-REL";

    private static Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private Config config;
    private File configFile;

    @Mod.EventHandler
    public void onInitialize(FMLPreInitializationEvent event) {
        configFile = new File(event.getModConfigurationDirectory(), "alaawiicoinslost/config.json");
        configFile.getParentFile().mkdirs();

        MinecraftForge.EVENT_BUS.register(this);

        ClientCommandHandler.instance.registerCommand(resetCommand);
        ClientCommandHandler.instance.registerCommand(setRatioCommand);
        ClientCommandHandler.instance.registerCommand(toggleCommand);
        ClientCommandHandler.instance.registerCommand(setCoinLostCommand);
        ClientCommandHandler.instance.registerCommand(removeCoinLostCommand);
        ClientCommandHandler.instance.registerCommand(addCoinLostCommand);
        ClientCommandHandler.instance.registerCommand(moveOverlayCommand);
        ClientCommandHandler.instance.registerCommand(toggleVisibilityCommand);

        //load config
        loadConfig();
        //save config
        Runtime.getRuntime().addShutdownHook(new Thread(this::saveConfig));
    }

    private SimpleCommand resetCommand = new SimpleCommand("aclreset", new SimpleCommand.ProcessCommandRunnable() {
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if(args.length != 1 || !args[0].equalsIgnoreCase("yes")) {
                sender.addChatMessage(new ChatComponentText("\u00a7cAre you sure? This also resets the totals to 0. " +
                        "If you want to just reset the coins lost, use /aclsetcoinslost 0"));
                sender.addChatMessage(new ChatComponentText("\u00a7aIf you are sure, use \"/aclreset yes\""));
                return;
            }
            config.coinsLost = 0;
            config.totalDamageTaken = 0;
            config.totalCoinsLost = 0;
            sender.addChatMessage(new ChatComponentText("\u00a7aReset everything to 0"));
        }
    });

    private SimpleCommand setCoinLostCommand = new SimpleCommand("aclsetcoinslost", new SimpleCommand.ProcessCommandRunnable() {
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if(args.length != 1) {
                sender.addChatMessage(new ChatComponentText("\u00a7cInvalid! Usage: /aclsetcoinslost {amount}"));
                return;
            }
            try {
                config.coinsLost = Integer.parseInt(args[0]);
                sender.addChatMessage(new ChatComponentText("\u00a7aSet! Current coins lost: " + config.coinsLost));
            } catch(Exception e) {
                sender.addChatMessage(new ChatComponentText("\u00a7cInvalid number!"));
            }
        }
    });

    private SimpleCommand addCoinLostCommand = new SimpleCommand("acladdcoinslost", new SimpleCommand.ProcessCommandRunnable() {
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if(args.length != 1) {
                sender.addChatMessage(new ChatComponentText("\u00a7cInvalid! Usage: /acladdcoinslost {amount}"));
                return;
            }
            try {
                config.coinsLost += Integer.parseInt(args[0]);
                sender.addChatMessage(new ChatComponentText("\u00a7aAdded! Current coins lost: " + config.coinsLost));
            } catch(Exception e) {
                sender.addChatMessage(new ChatComponentText("\u00a7cInvalid number!"));
            }
        }
    });

    private SimpleCommand removeCoinLostCommand = new SimpleCommand("aclremovecoinslost", new SimpleCommand.ProcessCommandRunnable() {
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if(args.length != 1) {
                sender.addChatMessage(new ChatComponentText("\u00a7cInvalid! Usage: /aclremovecoinslost {amount}"));
                return;
            }
            try {
                config.coinsLost -= Integer.parseInt(args[0]);
                sender.addChatMessage(new ChatComponentText("\u00a7aRemoved! Current coins lost: " + config.coinsLost));
            } catch(Exception e) {
                sender.addChatMessage(new ChatComponentText("\u00a7cInvalid number!"));
            }
        }
    });

    private SimpleCommand toggleCommand = new SimpleCommand("acltoggle", new SimpleCommand.ProcessCommandRunnable() {
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            config.counterActive = !config.counterActive;
        }
    });

    private SimpleCommand setRatioCommand = new SimpleCommand("aclratio", new SimpleCommand.ProcessCommandRunnable() {
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            if(args.length != 1) {
                sender.addChatMessage(new ChatComponentText("\u00a7cInvalid! Usage: /aclratio {coins/damage}"));
                return;
            }
            try {
                config.coinLostRatio = Float.parseFloat(args[0]);
                sender.addChatMessage(new ChatComponentText("\u00a7aSet! Now losing " + config.coinLostRatio + " coins per damage"));
            } catch(Exception e) {
                sender.addChatMessage(new ChatComponentText("\u00a7cInvalid number!"));
            }
        }
    });

    private SimpleCommand toggleVisibilityCommand = new SimpleCommand("acltogglevisibility", new SimpleCommand.ProcessCommandRunnable() {
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            config.hidden = !config.hidden;
            if(!config.hidden) {
                sender.addChatMessage(new ChatComponentText("\u00a7eOverlay is now: \u00a7aVisible"));
            } else {
                sender.addChatMessage(new ChatComponentText("\u00a7eOverlay is now: \u00a7cHidden"));
            }
        }
    });

    private GuiScreen openGui = null;

    private SimpleCommand moveOverlayCommand = new SimpleCommand("aclmove", new SimpleCommand.ProcessCommandRunnable() {
        @Override
        public void processCommand(ICommandSender sender, String[] args) {
            List<String> strings = getCoinsLostStrings();
            int height = strings.size()*10+8;
            int width = 0;
            for(String line : strings) {
                int lineWidth = Minecraft.getMinecraft().fontRendererObj.getStringWidth(line)+8;
                if(lineWidth > width) width = lineWidth;
            }

            openGui = new GuiPositionEditor(config.displayPosition, width, height, () -> {}, () -> {}, () -> {});
        }
    });

    public void loadConfig() {
        try {
            config = GSON.fromJson(Files.newBufferedReader(configFile.toPath()), Config.class);
        } catch(Exception ignored) {}

        if(config == null) {
            config = new Config();
        }
    }

    public void saveConfig() {
        try {
            configFile.createNewFile();
            Files.write(configFile.toPath(), GSON.toJson(config).getBytes());
        } catch(Exception ignored) {}
    }

    public List<String> getCoinsLostStrings() {
        return Lists.newArrayList("\u00a76Coins lost: \u00a7l" + format.format(config.coinsLost),
                "\u00a76Total coins lost: \u00a7l" + format.format(config.totalCoinsLost),
                "\u00a76Total Damage taken: \u00a7c\u00a7l" + format.format(config.totalDamageTaken),
                "\u00a76Losing \u00a7l" + format.format(config.coinLostRatio) + "\u00a76 coins per \u00a7cdamage",
                "\u00a76Tracking? " + (config.counterActive ? "\u00a7aACTIVE" : "\u00a7cPAUSED"));
    }

    private static Splitter SPACE_SPLITTER = Splitter.on("  ").omitEmptyStrings().trimResults();
    private static Pattern HEALTH_PATTERN = Pattern.compile("(\\d+)/(\\d+)\u2764");

    private int lastHealth = 0;
    private int lastMaxHealth = 0;

    private NumberFormat format = NumberFormat.getIntegerInstance();

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if(event.phase == TickEvent.Phase.END && openGui != null) {
            Minecraft.getMinecraft().displayGuiScreen(openGui);
            openGui = null;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onChatMessage(ClientChatReceivedEvent event) {
        if(event.type == 2) {
            List<String> components = SPACE_SPLITTER.splitToList(event.message.getUnformattedText().replaceAll("\u00a7.", ""));

            for(String component : components) {
                Matcher matcher = HEALTH_PATTERN.matcher(component);
                if(matcher.matches()) {
                    String healthS = matcher.group(1);
                    String maxHealthS = matcher.group(2);

                    int health = Integer.parseInt(healthS);
                    int maxHealth = Integer.parseInt(maxHealthS);

                    if(config.counterActive && lastMaxHealth == maxHealth && health < lastHealth) {
                        int healthLost = lastHealth - health;
                        config.totalDamageTaken += healthLost;
                        config.coinsLost += config.coinLostRatio * healthLost;
                        config.totalCoinsLost += config.coinLostRatio * healthLost;
                    }
                    lastMaxHealth = maxHealth;
                    lastHealth = health;
                }
            }
        }
    }

    @SubscribeEvent
    public void onScreenRender(RenderGameOverlayEvent.Post event) {
        if(!config.hidden && event.type == RenderGameOverlayEvent.ElementType.ALL) {
            ScaledResolution scaledResolution = new ScaledResolution(Minecraft.getMinecraft());

            List<String> strings = getCoinsLostStrings();
            int height = strings.size()*10+8;
            int width = 0;
            for(String line : strings) {
                int lineWidth = Minecraft.getMinecraft().fontRendererObj.getStringWidth(line)+8;
                if(lineWidth > width) width = lineWidth;
            }

            Position pos = config.displayPosition;
            int x = pos.getAbsX(scaledResolution, width);
            int y = pos.getAbsY(scaledResolution, height);

            Gui.drawRect(x, y, x+width, y+height, 0x80000000);
            for(String line : strings) {
                Minecraft.getMinecraft().fontRendererObj.drawString(line, x+4, y+4, 0xffffffff);
                y += 10;
            }
        }
    }

}
