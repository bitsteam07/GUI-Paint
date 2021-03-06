package frames;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Vector;

import javax.swing.JOptionPane;
import javax.swing.JPanel;

import constants.GEConstants;
import constants.GEConstants.EAnchorTypes;
import constants.GEConstants.EState;
import shapes.GEGroup;
import shapes.GEPolygon;
import shapes.GESelect;
import shapes.GEShape;
import transformer.GEDrawer;
import transformer.GEGrouper;
import transformer.GEMover;
import transformer.GEResizer;
import transformer.GETransformer;
import utils.GEClipBoard;
import utils.GECursorManager;
import utils.GEHistory;

public class GEDrawingPanel extends JPanel {

	private GEShape currentShape, selectedShape;
	private EState currentState;
	private ArrayList<GEShape> shapeList;
	private GETransformer transformer;
	private Color fillColor, lineColor;
	private MouseDrawingHandler drawingHandler;
	private GECursorManager cursorManager;
	private GEClipBoard clipboard;
	private GEHistory history;
	private double w;
	private double h;

	public GEDrawingPanel() {
		super();
		cursorManager = new GECursorManager();
		currentState = EState.Idle;
		shapeList = new ArrayList<GEShape>();
		drawingHandler = new MouseDrawingHandler();
		fillColor = GEConstants.DEFAULT_FILL_COLOR;
		lineColor = GEConstants.DEFAULT_LINE_COLOR;
		this.addMouseListener(drawingHandler);
		this.addMouseMotionListener(drawingHandler);
		this.setBackground(GEConstants.BACKGROUND_COLOR);
		this.setForeground(GEConstants.FOREGROUND_COLOR);
		clipboard = new GEClipBoard();
		history = new GEHistory();
	}

	@Override
	protected void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2D = (Graphics2D) g;
		for (GEShape shape : shapeList) {
			shape.draw(g2D);
		}
	}

	public void setFillColor(Color fillColor) {
		if (selectedShape != null) {
			selectedShape.setFillColor(fillColor);
			addHistory();
			repaint();
		} else {
			this.fillColor = fillColor;
		}
	}

	public void setLineColor(Color lineColor) {
		if (selectedShape != null) {
			selectedShape.setLineColor(lineColor);
			addHistory();
			repaint();
		} else {
			this.lineColor = lineColor;
		}
	}

	public void setCurrentShape(GEShape currentShape) {
		this.currentShape = currentShape;
	}

	public void setSelectedShape(GEShape selectedShape) {
		this.selectedShape = selectedShape;
	}

	private void initDraw(Point startP) {
		currentShape = currentShape.clone();
		if (currentShape instanceof GESelect) {
			currentShape.setLineColor(GEConstants.DEFAULT_LINE_COLOR);
		} else {
			currentShape.setFillColor(fillColor);
			currentShape.setLineColor(lineColor);
		}
	}

	private void continueDraw(Point currentP) {
		((GEDrawer) transformer).continueDrawing(currentP);
	}

	private void finishDraw() {
		shapeList.add(currentShape);
		addHistory();
	}

	private GEShape onShape(Point p) {
		for (int i = shapeList.size() - 1; i >= 0; i--) {
			GEShape shape = shapeList.get(i);
			if (shape.onShape(p)) {
				return shape;
			}
		}
		return null;
	}

	private void clearSelectedShapes() {
		for (GEShape shape : shapeList) {
			shape.setSelected(false);
		}
	}

	public void setCurrentState(EState currentState) {
		this.currentState = currentState;
	}

	public void addHistory() {
		history.push(shapeList);
	}

	// paste
	public void paste() {
		ArrayList<GEShape> pasteShapes = clipboard.paste();
		for (int i = pasteShapes.size() - 1; i >= 0; i--) {
			shapeList.add(pasteShapes.get(i).deepCopy());
		}
		addHistory();
		repaint();
	}

	// copy
	public void copy() {
		clipboard.copy(shapeList);

	}

	public void cut() {
		clipboard.cut(shapeList);
		addHistory();
		repaint();
	}

	public void delete() {
		for (int i = shapeList.size(); i > 0; i--) {
			GEShape shape = shapeList.get(i - 1);
			if (shape.isSelected()) {
				shape.setSelected(false);
				shapeList.remove(shape);
			}
		}
		addHistory();
		repaint();
	}

	public void undo() {
		shapeList = history.undo();
		selectedShape = null;
		repaint();
	}

	public void redo() {
		shapeList = history.redo();
		selectedShape = null;
		repaint();
	}

	public void group(GEGroup group) {
		boolean check = false;
		for (int i = shapeList.size(); i > 0; i--) {
			GEShape shape = shapeList.get(i - 1);
			if (shape.isSelected()) {
				shape.setSelected(false);
				group.addShape(shape);
				shapeList.remove(shape);
				check = true;
			}
		}
		if (check) {
			group.setSelected(true);
			shapeList.add(group);
		}
		setSelectedShape(group);
		repaint();
	}

	public void unGroup() {
		Vector<GEShape> tempList = new Vector<GEShape>();
		for (int i = shapeList.size(); i > 0; i--) {
			GEShape shape = shapeList.get(i - 1);
			if (shape instanceof GEGroup && shape.isSelected()) {
				for (GEShape childShape : ((GEGroup) shape).getChildList()) {
					childShape.setSelected(true);
					tempList.add(childShape);
				}
				shapeList.remove(shape);
			}
		}
		shapeList.addAll(tempList);
		repaint();
	}

	private class MouseDrawingHandler extends MouseAdapter {
		@Override
		public void mouseDragged(MouseEvent e) {
			if (currentState != EState.Idle) {
				transformer.transformer((Graphics2D) getGraphics(), e.getPoint());
				if (transformer instanceof GEMover) {
					((GEMover) transformer).setMove(true);
				}
			}
		}

		@Override
		public void mousePressed(MouseEvent e) {
			if (currentState == EState.Idle) {
				if (currentShape instanceof GESelect) {
					selectedShape = onShape(e.getPoint());
					if (selectedShape != null) {
						clearSelectedShapes();
						selectedShape.setSelected(true);
						selectedShape.onAnchor(e.getPoint());
						if (selectedShape.getSelectedAnchor() == EAnchorTypes.NONE) {
							transformer = new GEMover(selectedShape);
							((GEMover) transformer).init(e.getPoint());
							setCurrentState(EState.Moving);
						} else {
							transformer = new GEResizer(selectedShape);
							((GEResizer) transformer).init(e.getPoint());
							setCurrentState(EState.Resizing);
						}
					} else {
						setCurrentState(EState.Selecting);
						clearSelectedShapes();
						initDraw(e.getPoint());
						transformer = new GEGrouper(currentShape);
						((GEGrouper) transformer).init(e.getPoint());
					}
				} else {
					clearSelectedShapes();
					initDraw(e.getPoint());
					transformer = new GEDrawer(currentShape);
					((GEDrawer) transformer).init(e.getPoint());
					if (currentShape instanceof GEPolygon) {
						setCurrentState(EState.NPointsDrawing);
					} else {
						setCurrentState(EState.TwoPointsDrawing);
					}
				}
			}
		}

		@Override
		public void mouseReleased(MouseEvent e) {
			if (currentState == EState.TwoPointsDrawing) {
				finishDraw();
			} else if (currentState == EState.NPointsDrawing) {
				return;
			} else if (currentState == EState.Resizing) {
				((GEResizer) transformer).finalize();
				addHistory();
			} else if (currentState == EState.Selecting) {
				((GEGrouper) transformer).finalize(shapeList);
			} else if (currentState == EState.Moving) {
				if (((GEMover) transformer).isMoved()) {
					addHistory();
				}
			}
			currentState = EState.Idle;
			repaint();
		}

		@Override
		public void mouseClicked(MouseEvent e) {
			if (e.getButton() == MouseEvent.BUTTON1) {
				if (currentState == EState.NPointsDrawing) {
					if (e.getClickCount() == 1) {
						continueDraw(e.getPoint());
					} else if (e.getClickCount() == 2) {
						finishDraw();
						setCurrentState(EState.Idle);
						repaint();
					}
				}
			}
			
			if(selectedShape != null){
				if(e.getClickCount() == 2){
					transformer = new GEResizer(selectedShape);
					((GEResizer)transformer).init();
					((GEResizer)transformer).finalize();
					addHistory();
				}
			}
		}

		@Override
		public void mouseMoved(MouseEvent e) {
			if (currentState == EState.NPointsDrawing) {
				transformer.transformer((Graphics2D) getGraphics(), e.getPoint());
			} else if (currentState == EState.Idle) {
				GEShape shape = onShape(e.getPoint());
				if (shape == null) {
					setCursor(new Cursor(Cursor.DEFAULT_CURSOR));
				} else if (shape.isSelected() == true) {
					EAnchorTypes anchorType = shape.onAnchor(e.getPoint());
					setCursor(cursorManager.get(anchorType.ordinal()));
				}
			}
		}
	}
}