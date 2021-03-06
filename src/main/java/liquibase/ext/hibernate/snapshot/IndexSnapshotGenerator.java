package liquibase.ext.hibernate.snapshot;

import liquibase.exception.DatabaseException;
import liquibase.snapshot.DatabaseSnapshot;
import liquibase.snapshot.InvalidExampleException;
import liquibase.structure.DatabaseObject;
import liquibase.structure.core.*;

import java.util.Comparator;
import java.util.Iterator;

public class IndexSnapshotGenerator extends HibernateSnapshotGenerator {

    public IndexSnapshotGenerator() {
        super(Index.class, new Class[]{Table.class, ForeignKey.class, UniqueConstraint.class});
    }

    @Override
    protected DatabaseObject snapshotObject(DatabaseObject example, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (example.getSnapshotId() != null) {
            return example;
        }
        Table table = ((Index) example).getTable();
        org.hibernate.mapping.Table hibernateTable = findHibernateTable(table, snapshot);
        if (hibernateTable == null) {
            return example;
        }
        Iterator indexIterator = hibernateTable.getIndexIterator();
        while (indexIterator.hasNext()) {
            org.hibernate.mapping.Index hibernateIndex = (org.hibernate.mapping.Index) indexIterator.next();
            Index index = new Index();
            index.setTable(table);
            index.setName(hibernateIndex.getName());
            index.setUnique(isUniqueIndex(hibernateIndex));
            Iterator columnIterator = hibernateIndex.getColumnIterator();
            while (columnIterator.hasNext()) {
                org.hibernate.mapping.Column hibernateColumn = (org.hibernate.mapping.Column) columnIterator.next();
                index.getColumns().add(new Column(hibernateColumn.getName()).setRelation(table));
            }
            if (hibernateIndex.getColumnSpan() > 1) {
                index.getColumns().sort(Comparator.comparing(Column::getName).reversed());
            }

            if (index.getColumnNames().equalsIgnoreCase(((Index) example).getColumnNames())) {
                LOG.info("Found index " + index.getName());
                table.getIndexes().add(index);
                return index;
            }
        }
        return example;

    }

    @Override
    protected void addTo(DatabaseObject foundObject, DatabaseSnapshot snapshot) throws DatabaseException, InvalidExampleException {
        if (!snapshot.getSnapshotControl().shouldInclude(Index.class)) {
            return;
        }
        if (foundObject instanceof Table) {
            Table table = (Table) foundObject;
            org.hibernate.mapping.Table hibernateTable = findHibernateTable(table, snapshot);
            if (hibernateTable == null) {
                return;
            }
            Iterator indexIterator = hibernateTable.getIndexIterator();
            while (indexIterator.hasNext()) {
                org.hibernate.mapping.Index hibernateIndex = (org.hibernate.mapping.Index) indexIterator.next();
                Index index = new Index();
                index.setTable(table);
                index.setName(hibernateIndex.getName());
                index.setUnique(isUniqueIndex(hibernateIndex));
                Iterator<org.hibernate.mapping.Column> columnIterator = hibernateIndex.getColumnIterator();
                while (columnIterator.hasNext()) {
                    org.hibernate.mapping.Column hibernateColumn = columnIterator.next();
                    index.getColumns().add(new Column(hibernateColumn.getName()).setRelation(table));
                }
                if (hibernateIndex.getColumnSpan() > 1) {
                    index.getColumns().sort(Comparator.comparing(Column::getName).reversed());
                }
                LOG.info("Found index " + index.getName());
                table.getIndexes().add(index);
            }
        }
    }

    private Boolean isUniqueIndex(org.hibernate.mapping.Index hibernateIndex) {
        /*
        This seems to be necessary to explicitly tell liquibase that there's no
        actual diff in certain non-unique indexes
        */
        if (hibernateIndex.getColumnSpan() == 1) {
            org.hibernate.mapping.Column col = hibernateIndex.getColumnIterator().next();
            return col.isUnique();
        } else {
            return false;
        }
    }
}