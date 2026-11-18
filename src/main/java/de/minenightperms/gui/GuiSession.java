package de.minenightperms.gui;

public class GuiSession {

    public enum State {
        MAIN_MENU,
        GROUP_LIST,
        GROUP_EDITOR,
        COLOR_CATEGORY_ROLE,
        COLOR_CATEGORY_NAME,
        GRADIENT_PICKER_ROLE,
        GRADIENT_PICKER_NAME,
        SOLID_COLOR_PICKER_ROLE,
        SOLID_COLOR_PICKER_NAME,
        EMOJI_PICKER,
        PERMISSIONS,
        SET_ROLE_PLAYER_LIST,
        SET_ROLE_GROUP_PICK,
        SIGN_INPUT_GROUP_NAME,
        SIGN_INPUT_PREFIX,
        SIGN_INPUT_EMOJI,
        SIGN_INPUT_PERM_FILTER
    }

    private State  state            = State.MAIN_MENU;
    private String activeGroup      = null;
    private String targetPlayerName = null;
    private int    permPage         = 0;
    private String permFilter       = "";
    private int    gradientPage     = 0;

    public State  getState()                   { return state; }
    public void   setState(State s)            { this.state = s; }

    public String getActiveGroup()             { return activeGroup; }
    public void   setActiveGroup(String g)     { this.activeGroup = g; }

    public String getTargetPlayerName()        { return targetPlayerName; }
    public void   setTargetPlayerName(String n){ this.targetPlayerName = n; }

    public int    getPermPage()                { return permPage; }
    public void   setPermPage(int p)           { this.permPage = p; }

    public String getPermFilter()              { return permFilter; }
    public void   setPermFilter(String f)      { this.permFilter = f == null ? "" : f; }

    public int    getGradientPage()            { return gradientPage; }
    public void   setGradientPage(int p)       { this.gradientPage = p; }
}
