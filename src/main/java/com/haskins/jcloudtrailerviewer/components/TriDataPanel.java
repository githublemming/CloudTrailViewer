/*
 * Copyright (C) 2015 Mark P. Haskins
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.haskins.jcloudtrailerviewer.components;

import com.haskins.jcloudtrailerviewer.frame.TableWindow;
import com.haskins.jcloudtrailerviewer.filter.Filters;
import com.haskins.jcloudtrailerviewer.filter.FreeformFilter;
import com.haskins.jcloudtrailerviewer.frame.AbstractInternalFrame;
import com.haskins.jcloudtrailerviewer.jCloudTrailViewer;
import com.haskins.jcloudtrailerviewer.model.ChartData;
import com.haskins.jcloudtrailerviewer.model.Event;
import com.haskins.jcloudtrailerviewer.util.ChartCreator;
import com.haskins.jcloudtrailerviewer.util.EventUtils;
import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import javax.swing.JMenuBar;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.table.DefaultTableModel;
import org.jfree.chart.ChartMouseEvent;
import org.jfree.chart.ChartMouseListener;
import org.jfree.chart.ChartPanel;
import org.jfree.chart.entity.CategoryItemEntity;
import org.jfree.chart.entity.ChartEntity;
import org.jfree.chart.entity.PieSectionEntity;
import org.jfree.chart.plot.PlotOrientation;

/**
 *
 * @author mark.haskins
 */
public class TriDataPanel extends JPanel implements TriDataPanelMenuListener, ChartMouseListener {
    
    protected final Filters filters = new Filters();
    
    private final List<Event> masterEvents = new LinkedList<>();
    private List<Map.Entry<String, Integer>> chartEvents = new ArrayList<>();
    
    private ChartPanel chartPanel = null;
    private final ChartData chartData;
    private String chartSelect = null;
    
    private final DefaultTableModel defaultTableModel = new DefaultTableModel();   
        
    private final JTextArea tabbedTextArea = new JTextArea();
    private final JTabbedPane tabs = new JTabbedPane();
    
    private final TriDataPanelMenu menu = new TriDataPanelMenu();
    
    public TriDataPanel(ChartData data, boolean showMenu) {
        
        chartData = data;
        filters.addEventFilter(new FreeformFilter());
        
        menu.addListener(this);
        
        buildDisplay(showMenu);
    }
    
    public void setEvents(List<Event> events) {
        
        masterEvents.clear();
        masterEvents.addAll(events);
        
        updatePanel(5);
    }
    
    public JMenuBar getChartMenu() {
        return menu;
    }
        
    ////////////////////////////////////////////////////////////////////////////
    // TriDataPanelMenuListener
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void topUpdated(int newTop) {
        chartData.setTop(newTop);
        updatePanel(newTop);
    }
    
    @Override
    public void styleUpdated(String newStyle) {
        chartData.setChartStyle(newStyle);
        updateChart();
    }
    
    @Override
    public void sourceUpdated(String newSource) {
        chartData.setChartSource(newSource);
        updateChart();
    }
    
    ////////////////////////////////////////////////////////////////////////////
    // ChartMouseListener
    ////////////////////////////////////////////////////////////////////////////
    @Override
    public void chartMouseClicked(ChartMouseEvent cme) {

        if (cme.getTrigger().getClickCount() == 2) {
            
            Object object = cme.getEntity(); 
            ChartEntity cie = (ChartEntity)object; 

            if (chartData.getChartStyle().equalsIgnoreCase("Pie")) {

                PieSectionEntity categoryItemEntity = (PieSectionEntity)cie;
                chartSelect = categoryItemEntity.getSectionKey().toString();

            } else if (chartData.getChartStyle().equalsIgnoreCase("Bar")) {

                CategoryItemEntity categoryItemEntity = (CategoryItemEntity)cie;
                chartSelect = categoryItemEntity.getCategory().toString(); 
            }

            if (chartSelect != null) {

                filters.setFilterCriteria(chartSelect);
                List<Event> filteredEvents = filters.filterEvents(masterEvents);

                TableWindow window = new TableWindow("Filtered by : " + chartSelect, filteredEvents);
                window.setVisible(true);

                jCloudTrailViewer.DESKTOP.add(window);

                try {
                    window.setSelected(true);
                }
                catch (java.beans.PropertyVetoException pve) {
                }
            } 
        }
    }

    @Override
    public void chartMouseMoved(ChartMouseEvent cme) { }
        
    ////////////////////////////////////////////////////////////////////////////
    // private methods
    ////////////////////////////////////////////////////////////////////////////
    private void buildDisplay(boolean showMenu) {
        
        this.setLayout(new BorderLayout());
        
        selectInitialMenus();
        
        createChart();
        if (chartPanel != null) {
            tabs.addTab("Chart", chartPanel);
        }
                
        defaultTableModel.addColumn("Property");
        defaultTableModel.addColumn("Value");
        
        JTable table = new JTable(defaultTableModel);
        JScrollPane tablecrollPane = new JScrollPane(table);
        reloadTable();
        tabs.addTab("Table", tablecrollPane); 

        updateTextArea();
        JScrollPane tabbedDataScrollPane = new JScrollPane(tabbedTextArea);
        tabs.addTab("Data", tabbedDataScrollPane); 
        
        if (showMenu) {
            this.add(getChartMenu(), BorderLayout.NORTH);
        }
        
        this.add(tabs, BorderLayout.CENTER);
    }
    
    private void selectInitialMenus() {
        
        menu.setTop(chartData.getTop());
        menu.setStyle(chartData.getChartStyle());
        menu.setSource(chartData.getChartSource());
    }
        
    private void updatePanel(int top) {
               
        chartData.setTop(top);
        updateChart();
        reloadTable();
        updateTextArea();
    }
    
    private void reloadTable() {
        
        for (int i = defaultTableModel.getRowCount() -1; i>=0; i--) {
            defaultTableModel.removeRow(i);
        }
        
        if (chartEvents != null && chartEvents.size() > 0) {
            
            int count = 0;
            for (Map.Entry entry : chartEvents) {
                defaultTableModel.addRow(new Object[] { entry.getKey(), entry.getValue() });
                count++;
                
                if (count >= chartData.getTop()) {
                    break;
                }
            }         
        } 
    } 
    
    private void updateChart() {
        
        generateChartData();
        createChart();
        tabs.remove(0);
        tabs.insertTab("Chart", null, chartPanel, "", 0);
        tabs.setSelectedIndex(0);
    }
    
    private void updateTextArea() {

        if (chartEvents != null) {

            int count = 0;

            StringBuilder dataString = new StringBuilder();
            for (Map.Entry entry : chartEvents) {

                if (count >= chartData.getTop()) {
                    break;
                }

                dataString.append(entry.getKey()).append(" : ").append(entry.getValue()).append(AbstractInternalFrame.NEWLINE);
                count++;
            }

            tabbedTextArea.setText(dataString.toString());
        }
    }
        
    private void generateChartData() {
        chartEvents = EventUtils.getRequiredEvents(masterEvents, chartData);
    }
    
    private void createChart() {
        
        if (chartData.getChartStyle().contains("Pie")) {

            chartPanel = ChartCreator.createTopPieChart(
                chartData.getTop(), 
                chartEvents, 
                chartData.getChartStyle());
           
        } else if (chartData.getChartStyle().contains("Bar")) {

            chartPanel = ChartCreator.createBarChart(
                chartData.getTop(),
                chartEvents, 
                chartData.getChartSource(), "Count",
                PlotOrientation.VERTICAL,
                chartData.getChartStyle());
        } 
        
        if (chartPanel != null) {
            chartPanel.addChartMouseListener(this);
        }
    }
}