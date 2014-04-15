package org.jboss.pressgang.ccms.zanata;

import java.util.Comparator;

public class ZanataIdSort implements Comparator<String> {
    @Override
    public int compare(String zanataId1, String zanataId2) {
        if (zanataId1 == null && zanataId2 == null) {
            return 0;
        }

        if (zanataId1 == zanataId2) {
            return 0;
        }

        if (zanataId1 == null) {
            return -1;
        }

        if (zanataId2 == null) {
            return 1;
        }

        final ZanataId zanataid1 = new ZanataId(zanataId1);
        final ZanataId zanataid2 = new ZanataId(zanataId2);

        if (zanataid1 == null && zanataid2 == null) {
            return 0;
        }

        if (zanataid1 == zanataid2) {
            return 0;
        }

        if (zanataid1 == null) {
            return -1;
        }

        if (zanataid2 == null) {
            return 1;
        }

        // Compare the revision
        return zanataid1.compareTo(zanataid2);
    }

    private static class ZanataId implements Comparable<ZanataId> {
        private final Integer id;
        private final Integer revision;

        public ZanataId(String zanataId) {
            final String[] zanataNameSplit = zanataId.replace("CS", "").split("-");
            id = Integer.parseInt(zanataNameSplit[0]);
            revision = zanataNameSplit.length > 1 ? Integer.parseInt(zanataNameSplit[1]) : null;
        }

        @Override
        public int compareTo(ZanataId zanataId) {
            if (this == zanataId) {
                return 0;
            }

            if (zanataId == null) {
                return 1;
            }

            if (this.id == zanataId.id && this.revision == zanataId.revision) {
                return 0;
            }

            if (this.id == null) {
                return -1;
            }

            if (zanataId.id == null) {
                return 1;
            }

            if (this.id.equals(zanataId.id)) {
                if (this.revision == null) {
                    return -1;
                }

                if (zanataId.revision == null) {
                    return 1;
                }

                return this.revision.compareTo(zanataId.revision);
            } else {
                return this.id.compareTo(zanataId.id);
            }
        }
    }
}
