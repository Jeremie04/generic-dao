package Generic.util;

public class Pagination {
    int pageNumber; // page n°1, 2, 3 ...
    int pageSize; // nombre à afficher par page
    int totalSize;

    int beginIndex;
    int endIndex;

    private Pagination() {
    }

    public static Pagination fromPageNumber(int pageNumber, int pageSize) throws Exception {
        if (pageNumber < 0 || pageSize <= 0)
            throw new Exception("Invalid page or size");

        Pagination p = new Pagination();
        p.pageNumber = pageNumber;
        p.pageSize = pageSize;
        p.beginIndex = pageNumber * pageSize;
        p.endIndex = p.beginIndex + pageSize;
        return p;
    }

    public static Pagination fromBeginEnd(int beginIndex, int endIndex) throws Exception {
        if (beginIndex < 0 || endIndex < beginIndex)
            throw new Exception("Invalid begin or end");

        Pagination p = new Pagination();
        p.beginIndex = beginIndex;
        p.endIndex = endIndex;
        p.pageSize = endIndex - beginIndex;
        p.pageNumber = beginIndex / p.pageSize;
        return p;
    }

    public int getPageNumber() {
        return pageNumber;
    }

    public void setPageNumber(String number) throws Exception {
        if (number != null) {
            setPageNumber(Integer.parseInt(number));
        }
    }

    public void setPageSize(String size) throws Exception {
        if (size != null) {
            setPageSize(Integer.parseInt(size));
        }
    }

    public void setPageNumber(int pageNumber) throws Exception {
        if (pageNumber < 0)
            throw new Exception("page Number negatif :" + pageNumber);
        this.pageNumber = pageNumber;
    }

    public int getPageSize() {
        return pageSize;
    }

    public void setPageSize(int pageSize) throws Exception {
        if (pageSize < 0)
            throw new Exception("page Size negatif :" + pageSize);
        this.pageSize = pageSize;
    }

    public int getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(int totalSize) throws Exception {
        if (totalSize < 0)
            throw new Exception("Total Size negatif :" + totalSize);
        this.totalSize = totalSize;
    }

    public int getEnd() {
        return getPageNumber() * getPageSize();
    }

    public int getStart() {
        return getEnd() - getPageSize();
    }

    public int getMaxPageNumber() {
        double page = (double) getTotalSize() / getPageSize();
        return (int) Math.ceil(page);
    }

    public boolean isbeginningPage() {
        return getPageNumber() == 1;
    }

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
