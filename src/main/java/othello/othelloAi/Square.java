package othello.othelloAi;

public enum Square
{

    BLACK(0), WHITE(1), EMPTY(-1);

    /**
     * The owner of the disc. -1 if the square is empty.
     */
    private final int player;

    /**
     * Creates a new square.
     * @param player the player that owns the square
     */
    Square(int player)
    {
        this.player = player;
    }

    /**
     * Returns the player that owns the square. Returns -1 if empty.
     * @return the index of the player that owns the square
     */
    public int getPlayer()
    {
        return player;
    }
}

