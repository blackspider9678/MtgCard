package com.spider.mtgcard.dice;

import org.joml.Vector3f;
import java.util.*;

public final class DicePolyhedra {
    private static final float RADIUS = (float)(Math.sqrt(3.0) * 0.25);
    public record Shape(Vector3f[] vertices, int[][] faces, Vector3f[] normals, int[][] edges) {}
    private static final Map<Integer, Shape> SHAPES = new HashMap<>();
    static {
        SHAPES.put(10, dual(antiprism(5)));
        Shape ico = icosahedron();
        SHAPES.put(20, ico);
        SHAPES.put(12, dual(ico));
        SHAPES.put(100, fibonacciHull(52));
        for(var entry:SHAPES.entrySet()) {
            if(entry.getValue().faces.length!=entry.getKey())
                throw new IllegalStateException("d"+entry.getKey()+" geometry has "+entry.getValue().faces.length+" faces");
            validatePlanarFaces(entry.getKey(), entry.getValue());
        }
    }
    public static Shape get(int sides) { return SHAPES.get(sides); }

    private static Shape antiprism(int n) {
        Vector3f[] v = new Vector3f[n * 2];
        float radius=.25f, h=.13f;
        for(int i=0;i<n;i++) {
            double a=2*Math.PI*i/n; v[i]=new Vector3f((float)Math.cos(a)*radius,h,(float)Math.sin(a)*radius);
            a+=Math.PI/n; v[n+i]=new Vector3f((float)Math.cos(a)*radius,-h,(float)Math.sin(a)*radius);
        }
        List<int[]> f=new ArrayList<>();
        int[] top=new int[n], bottom=new int[n]; for(int i=0;i<n;i++){top[i]=i;bottom[i]=2*n-1-i;} f.add(top);f.add(bottom);
        for(int i=0;i<n;i++){int j=(i+1)%n;f.add(new int[]{i,n+i,j});f.add(new int[]{j,n+i,n+j});}
        return build(v,f.toArray(int[][]::new));
    }

    private static Shape icosahedron() {
        float p=(1f+(float)Math.sqrt(5))/2f;
        Vector3f[] v={new Vector3f(-1,p,0),new Vector3f(1,p,0),new Vector3f(-1,-p,0),new Vector3f(1,-p,0),
                new Vector3f(0,-1,p),new Vector3f(0,1,p),new Vector3f(0,-1,-p),new Vector3f(0,1,-p),
                new Vector3f(p,0,-1),new Vector3f(p,0,1),new Vector3f(-p,0,-1),new Vector3f(-p,0,1)};
        for(Vector3f x:v)x.normalize(RADIUS);
        int[][] f={{0,11,5},{0,5,1},{0,1,7},{0,7,10},{0,10,11},{1,5,9},{5,11,4},{11,10,2},{10,7,6},{7,1,8},
                {3,9,4},{3,4,2},{3,2,6},{3,6,8},{3,8,9},{4,9,5},{2,4,11},{6,2,10},{8,6,7},{9,8,1}};
        return build(v,f);
    }

    private static Shape dual(Shape source) {
        Vector3f[] v=new Vector3f[source.faces.length];
        float farthest = 0f;
        for(int i=0;i<v.length;i++){
            // Polar dual: the dual vertex of n·x=d is n/d. Unlike normalizing each
            // face center independently, this guarantees that every resulting kite is
            // genuinely planar and the complete solid remains convex.
            Vector3f normal = new Vector3f(source.normals[i]);
            float distance = normal.dot(source.vertices[source.faces[i][0]]);
            v[i] = normal.div(distance);
            farthest = Math.max(farthest, v[i].length());
        }
        float scale = RADIUS / farthest;
        for (Vector3f vertex : v) vertex.mul(scale);
        List<int[]> faces=new ArrayList<>();
        for(int original=0;original<source.vertices.length;original++){
            List<Integer> incident=new ArrayList<>(); for(int i=0;i<source.faces.length;i++)for(int id:source.faces[i])if(id==original){incident.add(i);break;}
            Vector3f n=new Vector3f(source.vertices[original]).normalize(), u=Math.abs(n.y)<.9?new Vector3f(0,1,0).cross(n).normalize():new Vector3f(1,0,0).cross(n).normalize();
            Vector3f w=new Vector3f(n).cross(u);
            incident.sort(Comparator.comparingDouble(i->Math.atan2(v[i].dot(w),v[i].dot(u))));
            faces.add(incident.stream().mapToInt(Integer::intValue).toArray());
        }
        return build(v,faces.toArray(int[][]::new));
    }

    private static void validatePlanarFaces(int sides, Shape shape) {
        for (int faceIndex = 0; faceIndex < shape.faces.length; faceIndex++) {
            int[] face = shape.faces[faceIndex];
            if (face.length <= 3) continue;
            Vector3f origin = shape.vertices[face[0]];
            Vector3f normal = shape.normals[faceIndex];
            for (int i = 1; i < face.length; i++) {
                float distance = Math.abs(normal.dot(new Vector3f(shape.vertices[face[i]]).sub(origin)));
                if (distance > 1.0E-5f) {
                    throw new IllegalStateException("d" + sides + " face " + (faceIndex + 1)
                            + " is non-planar by " + distance);
                }
            }
        }
    }

    private static Shape fibonacciHull(int count) {
        Vector3f[] v=new Vector3f[count]; double golden=Math.PI*(3-Math.sqrt(5));
        for(int i=0;i<count;i++){double y=1-2.0*i/(count-1),r=Math.sqrt(1-y*y),a=golden*i;v[i]=new Vector3f((float)(Math.cos(a)*r),(float)y,(float)(Math.sin(a)*r)).mul(RADIUS);}
        List<int[]> faces=new ArrayList<>();
        for(int a=0;a<count-2;a++)for(int b=a+1;b<count-1;b++)for(int c=b+1;c<count;c++){
            Vector3f n=new Vector3f(v[b]).sub(v[a]).cross(new Vector3f(v[c]).sub(v[a])); if(n.lengthSquared()<1e-10)continue;
            int sign=0;boolean hull=true;for(int i=0;i<count;i++){if(i==a||i==b||i==c)continue;float d=n.dot(new Vector3f(v[i]).sub(v[a]));if(Math.abs(d)<1e-8)continue;int s=d>0?1:-1;if(sign==0)sign=s;else if(sign!=s){hull=false;break;}}
            if(hull)faces.add(sign>0?new int[]{a,c,b}:new int[]{a,b,c});
        }
        return build(v,faces.toArray(int[][]::new));
    }

    private static Shape build(Vector3f[] vertices,int[][] faces){
        Vector3f[] normals=new Vector3f[faces.length];Set<Long> seen=new LinkedHashSet<>();List<int[]> edges=new ArrayList<>();
        for(int i=0;i<faces.length;i++){int[] f=faces[i];Vector3f n=new Vector3f(vertices[f[1]]).sub(vertices[f[0]]).cross(new Vector3f(vertices[f[2]]).sub(vertices[f[0]])).normalize();Vector3f c=new Vector3f();for(int id:f)c.add(vertices[id]);if(n.dot(c)<0){n.negate();reverse(f);}normals[i]=n;
            for(int j=0;j<f.length;j++){int a=f[j],b=f[(j+1)%f.length],lo=Math.min(a,b),hi=Math.max(a,b);long k=((long)lo<<32)|(hi&0xffffffffL);if(seen.add(k))edges.add(new int[]{lo,hi});}}
        return new Shape(vertices,faces,normals,edges.toArray(int[][]::new));
    }
    private static void reverse(int[] a){for(int i=0,j=a.length-1;i<j;i++,j--){int t=a[i];a[i]=a[j];a[j]=t;}}
    private DicePolyhedra(){}
}
