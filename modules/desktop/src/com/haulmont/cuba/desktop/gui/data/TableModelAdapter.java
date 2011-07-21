/*
 * Copyright (c) 2011 Haulmont Technology Ltd. All Rights Reserved.
 * Haulmont Technology proprietary and confidential.
 * Use is subject to license terms.
 */

package com.haulmont.cuba.desktop.gui.data;

import com.haulmont.chile.core.model.MetaClass;
import com.haulmont.chile.core.model.MetaProperty;
import com.haulmont.chile.core.model.MetaPropertyPath;
import com.haulmont.cuba.core.entity.Entity;
import com.haulmont.cuba.core.global.MessageUtils;
import com.haulmont.cuba.core.global.View;
import com.haulmont.cuba.gui.components.Table;
import com.haulmont.cuba.gui.data.CollectionDatasource;
import com.haulmont.cuba.gui.data.impl.CollectionDsHelper;
import com.haulmont.cuba.gui.data.impl.CollectionDsListenerAdapter;

import javax.swing.*;
import javax.swing.table.AbstractTableModel;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * <p>$Id$</p>
 *
 * @author krivopustov
 */
public class TableModelAdapter extends AbstractTableModel implements AnyTableModelAdapter {

    private static final long serialVersionUID = -3892470031734710618L;

    protected CollectionDatasource<Entity<Object>, Object> datasource;
    protected List<MetaPropertyPath> properties = new ArrayList<MetaPropertyPath>();
    protected List<Table.Column> columns;
    protected List<Table.Column> generatedColumns = new ArrayList<Table.Column>();
    protected boolean autoRefresh;

    public TableModelAdapter(
            CollectionDatasource datasource,
            List<Table.Column> columns,
            boolean autoRefresh)
    {
        this.datasource = datasource;
        this.columns = columns;
        this.autoRefresh = autoRefresh;

        final View view = datasource.getView();
        final MetaClass metaClass = datasource.getMetaClass();

        if (columns == null) {
            createProperties(view, metaClass);
        } else {
            for (Table.Column column : columns) {
                if (column.getId() instanceof MetaPropertyPath)
                    properties.add((MetaPropertyPath) column.getId());
            }
        }

        datasource.addListener(
                new CollectionDsListenerAdapter() {
                    @Override
                    public void collectionChanged(CollectionDatasource ds, Operation operation) {
                        fireTableDataChanged();
                    }

                    @Override
                    public void valueChanged(Entity source, String property, Object prevValue, Object value) {
                        fireTableDataChanged();
                    }
                }
        );
    }

    protected void createProperties(View view, MetaClass metaClass) {
        properties.addAll(CollectionDsHelper.createProperties(view, metaClass));
    }

    @Override
    public int getRowCount() {
        CollectionDsHelper.autoRefreshInvalid(datasource, autoRefresh);
        return datasource.size();
    }

    @Override
    public int getColumnCount() {
        return properties.size();
    }

    @Override
    public String getColumnName(int column) {
        Table.Column c = columns.get(column);
        return c.getCaption();
    }

    public Object getValueAt(int rowIndex, int columnIndex) {
        Object id = getItemId(rowIndex);

        Entity item = datasource.getItem(id);
        return getValueAt(item, columnIndex);
    }

    public Object getValueAt(Entity item, int columnIndex) {
        Table.Column column = columns.get(columnIndex);
        if (column.getId() instanceof MetaPropertyPath) {
            String property = column.getId().toString();
            Object value = item.getValueEx(property);
            MetaPropertyPath metaProperty = ((MetaPropertyPath) column.getId());

            boolean isDataType = (metaProperty.getRange().isDatatype());
            if (isDataType && hasDefaultFormatting(value))
                return value;
            else
                return MessageUtils.format(value, ((MetaPropertyPath) column.getId()).getMetaProperty());

        } else {
            return null;
        }
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
        Table.Column column = columns.get(columnIndex);
        if (column.isEditable() || generatedColumns.contains(column))
            return true;
        else
            return false;
    }

    public Object getItemId(int rowIndex) {
        Object id = null;
        if (datasource instanceof CollectionDatasource.Ordered) {
            int idx = 0;
            id = ((CollectionDatasource.Ordered) datasource).firstItemId();
            while (++idx <= rowIndex) {
                id = ((CollectionDatasource.Ordered) datasource).nextItemId(id);
            }
        } else {
            Collection itemIds = datasource.getItemIds();
            int idx = 0;
            for (Object itemId : itemIds) {
                id = itemId;
                if (idx++ == rowIndex)
                    break;
            }
        }
        return id;
    }

    @Override
    public Entity getItem(int rowIndex) {
        Object itemId = getItemId(rowIndex);
        return datasource.getItem(itemId);
    }

    @Override
    public int getRowIndex(Entity entity) {
        int idx = 0;
        if (datasource instanceof CollectionDatasource.Ordered) {
            Object id = ((CollectionDatasource.Ordered) datasource).firstItemId();
            while (id != null) {
                if (entity.equals(datasource.getItem(id)))
                    return idx;
                id = ((CollectionDatasource.Ordered) datasource).nextItemId(id);
                idx++;
            }
        } else {
            Collection itemIds = datasource.getItemIds();
            for (Object id : itemIds) {
                if (entity.equals(datasource.getItem(id)))
                    return idx;
                idx++;
            }
        }
        return -1;
    }

    @Override
    public void addGeneratedColumn(Table.Column column) {
        generatedColumns.add(column);
    }

    @Override
    public void removeGeneratedColumn(Table.Column column) {
        generatedColumns.remove(column);
    }

    @Override
    public void sort(List<? extends RowSorter.SortKey> sortKeys) {
        if (!(datasource instanceof CollectionDatasource.Sortable) || sortKeys == null)
            return;

        List<CollectionDatasource.Sortable.SortInfo> sortInfos = new ArrayList<CollectionDatasource.Sortable.SortInfo>();
        for (RowSorter.SortKey sortKey : sortKeys) {
            if (!sortKey.getSortOrder().equals(SortOrder.UNSORTED)) {
                Table.Column c = columns.get(sortKey.getColumn());
                CollectionDatasource.Sortable.SortInfo<Object> sortInfo = new CollectionDatasource.Sortable.SortInfo<Object>();
                sortInfo.setPropertyPath(c.getId());
                sortInfo.setOrder(sortKey.getSortOrder().equals(SortOrder.ASCENDING)
                        ? CollectionDatasource.Sortable.Order.ASC
                        : CollectionDatasource.Sortable.Order.DESC);
                sortInfos.add(sortInfo);
            }
        }
        ((CollectionDatasource.Sortable) datasource).sort(
                sortInfos.toArray(new CollectionDatasource.Sortable.SortInfo[sortInfos.size()]));
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
        Table.Column column = columns.get(columnIndex);
        Class columnType = column.getType();

        if (hasDefaultFormatting(columnType))
            return columnType;
        return super.getColumnClass(columnIndex);
    }

    private boolean hasDefaultFormatting(Object value) {
        return Boolean.class.isInstance(value);
    }

    private boolean hasDefaultFormatting(Class valueClass) {
        return Boolean.class.equals(valueClass);
    }
}
