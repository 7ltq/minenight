package de.minenightperms.model;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;

import java.util.ArrayList;
import java.util.List;

public class Group {

    private static final MiniMessage MM = MiniMessage.miniMessage();

    private String name;
    private String roleGradient  = "white";
    private boolean roleBold     = false;
    private String nameGradient  = "white";
    private boolean nameBold     = false;
    private String emoji         = "";
    
    private String prefix        = "";
    private List<String> permissions = new ArrayList<>();
    private int priority         = 0;

    public Group(String name) { this.name = name; }

    private String wrapColor(String color, String text, boolean bold) {
        if (color == null || color.isEmpty() || color.equals("white")) {
            return bold ? "<bold>" + text + "</bold>" : text;
        }
        String wrapped = color.startsWith("<gradient")
            ? color + text + "</gradient>"
            : "<" + color + ">" + text + "</" + color + ">";
        return bold ? "<bold>" + wrapped + "</bold>" : wrapped;
    }

    
    public String getFormattedRole() {
        if (name.equalsIgnoreCase("default")) return "";

        String displayText = (prefix != null && !prefix.isBlank()) ? prefix : name;
        String colored = wrapColor(roleGradient, displayText, roleBold);
        if (emoji != null && !emoji.isBlank()) {
            return emoji + " " + colored;
        }
        return colored;
    }

    
    public String getFormattedName(String playerName) {
        return wrapColor(nameGradient, playerName, nameBold);
    }

    public Component getFormattedNameComponent(String playerName) {
        return MM.deserialize(getFormattedName(playerName));
    }


    public String  getName()                      { return name; }
    public void    setName(String n)              { this.name = n; }

    public String  getRoleGradient()              { return roleGradient; }
    public void    setRoleGradient(String g)      { this.roleGradient = g != null ? g : "white"; }

    public boolean isRoleBold()                   { return roleBold; }
    public void    setRoleBold(boolean b)         { this.roleBold = b; }

    public String  getNameGradient()              { return nameGradient; }
    public void    setNameGradient(String g)      { this.nameGradient = g != null ? g : "white"; }

    public boolean isNameBold()                   { return nameBold; }
    public void    setNameBold(boolean b)         { this.nameBold = b; }

    public String  getEmoji()                     { return emoji == null ? "" : emoji; }
    public void    setEmoji(String e)             { this.emoji = e == null ? "" : e; }

    public String  getPrefix()                    { return prefix == null ? "" : prefix; }
    public void    setPrefix(String p)            { this.prefix = p == null ? "" : p; }

    public List<String> getPermissions()          { return permissions; }
    public void setPermissions(List<String> p)    { this.permissions = new ArrayList<>(p); }

    public int     getPriority()                  { return priority; }
    public void    setPriority(int p)             { this.priority = p; }
}
