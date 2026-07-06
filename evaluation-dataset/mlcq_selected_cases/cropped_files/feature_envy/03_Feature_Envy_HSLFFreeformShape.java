// MLCQ cropped selected sample
// Original local source: feature_envy/03_Feature_Envy_HSLFFreeformShape.java
// Original target local lines: 119-210
// Crop local lines: 89-233

    /**
     * Create a Freeform object and initialize it from the supplied Record container.
     *
     * @param escherRecord       <code>EscherSpContainer</code> container which holds information about this shape
     * @param parent    the parent of the shape
     */
   protected HSLFFreeformShape(EscherContainerRecord escherRecord, ShapeContainer<HSLFShape,HSLFTextParagraph> parent){
        super(escherRecord, parent);

    }

    /**
     * Create a new Freeform. This constructor is used when a new shape is created.
     *
     * @param parent    the parent of this Shape. For example, if this text box is a cell
     * in a table then the parent is Table.
     */
    public HSLFFreeformShape(ShapeContainer<HSLFShape,HSLFTextParagraph> parent){
        super((EscherContainerRecord)null, parent);
        createSpContainer(ShapeType.NOT_PRIMITIVE, parent instanceof HSLFGroupShape);
    }

    /**
     * Create a new Freeform. This constructor is used when a new shape is created.
     *
     */
    public HSLFFreeformShape(){
        this(null);
    }

    @Override
    public int setPath(Path2D path) {
        Rectangle2D bounds = path.getBounds2D();
        PathIterator it = path.getPathIterator(null);

        List<byte[]> segInfo = new ArrayList<>();
        List<Point2D.Double> pntInfo = new ArrayList<>();
        boolean isClosed = false;
        int numPoints = 0;
        while (!it.isDone()) {
            double[] vals = new double[6];
            int type = it.currentSegment(vals);
            switch (type) {
                case PathIterator.SEG_MOVETO:
                    pntInfo.add(new Point2D.Double(vals[0], vals[1]));
                    segInfo.add(SEGMENTINFO_MOVETO);
                    numPoints++;
                    break;
                case PathIterator.SEG_LINETO:
                    pntInfo.add(new Point2D.Double(vals[0], vals[1]));
                    segInfo.add(SEGMENTINFO_LINETO);
                    segInfo.add(SEGMENTINFO_ESCAPE);
                    numPoints++;
                    break;
                case PathIterator.SEG_CUBICTO:
                    pntInfo.add(new Point2D.Double(vals[0], vals[1]));
                    pntInfo.add(new Point2D.Double(vals[2], vals[3]));
                    pntInfo.add(new Point2D.Double(vals[4], vals[5]));
                    segInfo.add(SEGMENTINFO_CUBICTO);
                    segInfo.add(SEGMENTINFO_ESCAPE2);
                    numPoints++;
                    break;
                case PathIterator.SEG_QUADTO:
                    //TODO: figure out how to convert SEG_QUADTO into SEG_CUBICTO
                    LOG.log(POILogger.WARN, "SEG_QUADTO is not supported");
                    break;
                case PathIterator.SEG_CLOSE:
                    pntInfo.add(pntInfo.get(0));
                    segInfo.add(SEGMENTINFO_LINETO);
                    segInfo.add(SEGMENTINFO_ESCAPE);
                    segInfo.add(SEGMENTINFO_LINETO);
                    segInfo.add(SEGMENTINFO_CLOSE);
                    isClosed = true;
                    numPoints++;
                    break;
                default:
                    LOG.log(POILogger.WARN, "Ignoring invalid segment type "+type);
                    break;
            }

            it.next();
        }
        if(!isClosed) {
            segInfo.add(SEGMENTINFO_LINETO);
        }
        segInfo.add(SEGMENTINFO_END);

        AbstractEscherOptRecord opt = getEscherOptRecord();
        opt.addEscherProperty(new EscherSimpleProperty(EscherProperties.GEOMETRY__SHAPEPATH, 0x4));

        EscherArrayProperty verticesProp = new EscherArrayProperty((short)(EscherProperties.GEOMETRY__VERTICES + 0x4000), false, null);
        verticesProp.setNumberOfElementsInArray(pntInfo.size());
        verticesProp.setNumberOfElementsInMemory(pntInfo.size());
        verticesProp.setSizeOfElements(8);
        for (int i = 0; i < pntInfo.size(); i++) {
            Point2D.Double pnt = pntInfo.get(i);
            byte[] data = new byte[8];
            LittleEndian.putInt(data, 0, Units.pointsToMaster(pnt.getX() - bounds.getX()));
            LittleEndian.putInt(data, 4, Units.pointsToMaster(pnt.getY() - bounds.getY()));
            verticesProp.setElement(i, data);
        }
        opt.addEscherProperty(verticesProp);

        EscherArrayProperty segmentsProp = new EscherArrayProperty((short)(EscherProperties.GEOMETRY__SEGMENTINFO + 0x4000), false, null);
        segmentsProp.setNumberOfElementsInArray(segInfo.size());
        segmentsProp.setNumberOfElementsInMemory(segInfo.size());
        segmentsProp.setSizeOfElements(0x2);
        for (int i = 0; i < segInfo.size(); i++) {
            byte[] seg = segInfo.get(i);
            segmentsProp.setElement(i, seg);
        }
        opt.addEscherProperty(segmentsProp);

        opt.addEscherProperty(new EscherSimpleProperty(EscherProperties.GEOMETRY__RIGHT, Units.pointsToMaster(bounds.getWidth())));
        opt.addEscherProperty(new EscherSimpleProperty(EscherProperties.GEOMETRY__BOTTOM, Units.pointsToMaster(bounds.getHeight())));

        opt.sortProperties();

        setAnchor(bounds);

        return numPoints;
    }

    @Override
    public Path2D getPath(){
        Path2D path2D = new Path2D.Double();
        getGeometry(path2D);

        Rectangle2D bounds = path2D.getBounds2D();
        Rectangle2D anchor = getAnchor();
        AffineTransform at = new AffineTransform();
        at.translate(anchor.getX(), anchor.getY());
        at.scale(
                anchor.getWidth()/bounds.getWidth(),
                anchor.getHeight()/bounds.getHeight()
        );

        path2D.transform(at);


        return path2D;
    }


}
