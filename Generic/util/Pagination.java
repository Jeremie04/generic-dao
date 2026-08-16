package Generic.util;

/**
 * Parametres de pagination a passer a {@code GenericDAO.setPagination(...)} avec
 * {@code setPaginable(true)}. Construire avec {@link #fromPageNumber(int, int)} ou
 * {@link #fromBeginEnd(int, int)} ({@code new Pagination()} n'est pas accessible).
 * Apres un {@code select()} paginable, {@link #getTotalSize()} est rempli automatiquement
 * par le DAO avec le nombre total de resultats (toutes pages confondues).
 */
public class Pagination {
    int pageNumber; // page n°1, 2, 3 ...
    int pageSize; // nombre à afficher par page
    int totalSize;

    int beginIndex;
    int endIndex;

    private Pagination() {
    }

    /**
     * Cree une pagination a partir d'un numero de page (1 = premiere page) et d'une taille de page.
     *
     * @throws Exception si {@code pageNumber} est negatif ou {@code pageSize} n'est pas strictement positif
     */
    /**
     * Cree une pagination a partir d'un numero de page 1-indexe (1 = premiere page) et d'une
     * taille de page.
     *
     * @throws Exception si {@code pageNumber} est inferieur a 1 ou {@code pageSize} n'est pas
     *                    strictement positif
     */
    public static Pagination fromPageNumber(int pageNumber, int pageSize) throws Exception {
        if (pageNumber < 1 || pageSize <= 0)
            throw new Exception("Invalid page or size");

        Pagination p = new Pagination();
        p.pageNumber = pageNumber;
        p.pageSize = pageSize;
        p.beginIndex = (pageNumber - 1) * pageSize;
        p.endIndex = p.beginIndex + pageSize;
        return p;
    }

    /**
     * Cree une pagination a partir de bornes brutes (indices de lignes, 0-indexes).
     *
     * @throws Exception si {@code beginIndex} est negatif ou {@code endIndex} est inferieur a {@code beginIndex}
     */
    public static Pagination fromBeginEnd(int beginIndex, int endIndex) throws Exception {
        if (beginIndex < 0 || endIndex < beginIndex)
            throw new Exception("Invalid begin or end");

        Pagination p = new Pagination();
        p.beginIndex = beginIndex;
        p.endIndex = endIndex;
        p.pageSize = endIndex - beginIndex;
        p.pageNumber = (beginIndex / p.pageSize) + 1;
        return p;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    /** Comme {@link #setPageNumber(int)}, en parsant {@code number} (ignore si {@code null}). */
    public void setPageNumber(String number) throws Exception {
        if (number != null) {
            setPageNumber(Integer.parseInt(number));
        }
    }

    /** Comme {@link #setPageSize(int)}, en parsant {@code size} (ignore si {@code null}). */
    public void setPageSize(String size) throws Exception {
        if (size != null) {
            setPageSize(Integer.parseInt(size));
        }
    }

    // Attention : ne recalcule pas beginIndex/endIndex (ce que GenericDAO utilise reellement
    // pour l'OFFSET/LIMIT). Changer de page necessite donc de reconstruire la pagination via
    // fromPageNumber plutot que d'appeler ce setter seul.
    public void setPageNumber(int pageNumber) throws Exception {
        if (pageNumber < 0)
            throw new Exception("page Number negatif :" + pageNumber);
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    // Meme remarque que setPageNumber(int) : beginIndex/endIndex ne sont pas recalcules.
    public void setPageSize(int pageSize) throws Exception {
        if (pageSize < 0)
            throw new Exception("page Size negatif :" + pageSize);
        this.pageSize = pageSize;
    }

    /** Nombre total de resultats toutes pages confondues ; rempli par GenericDAO apres un select paginable. */
    public int getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(int totalSize) throws Exception {
        if (totalSize < 0)
            throw new Exception("Total Size negatif :" + totalSize);
        this.totalSize = totalSize;
    }

    /** Indice de fin (exclu) de la page courante, equivalent a {@link #getEndIndex()}. */
    public int getEnd() {
        return getPageNumber() * getPageSize();
    }

    /** Indice de debut (inclus) de la page courante, equivalent a {@link #getBeginIndex()}. */
    public int getStart() {
        return getEnd() - getPageSize();
    }

    /** Nombre total de pages compte tenu de {@link #getTotalSize()} et {@link #getPageSize()}. */
    public int getMaxPageNumber() {
        double page = (double) getTotalSize() / getPageSize();
        return (int) Math.ceil(page);
    }

    /** {@code true} si la page courante est la premiere (numero 1). */
    public boolean isbeginningPage() {
        return getPageNumber() == 1;
    }

    /** {@code true} si la page courante est la derniere (necessite {@link #getTotalSize()} deja rempli). */
    public boolean isLastPage() {
        return getPageNumber() == getMaxPageNumber();
    }

    public void setBeginIndex(int beginIndex) {
        this.beginIndex = beginIndex;
    }

    public void setEndIndex(int endIndex) {
        this.endIndex = endIndex;
    }

    public int getBeginIndex() {
        return beginIndex;
    }

    public int getEndIndex() {
        return endIndex;
    }
}
