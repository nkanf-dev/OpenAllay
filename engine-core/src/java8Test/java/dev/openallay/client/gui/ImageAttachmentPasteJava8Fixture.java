package dev.openallay.client.gui;

import dev.openallay.client.gui.clipboard.AwtImageClipboard;
import dev.openallay.client.gui.clipboard.ClipboardImageEncoder;
import dev.openallay.client.gui.clipboard.ImageClipboard;
import dev.openallay.json.EngineJson;
import dev.openallay.model.image.FileImageAttachmentStore;
import dev.openallay.model.image.ImageReference;
import dev.openallay.tool.ToolResult;
import dev.openallay.value.RecordMetadata;
import dev.openallay.value.ValueSchema;
import dev.openallay.value.ValueSchemas;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.image.BufferedImage;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

/** Synthetic inputs only; authentic owners in original-modern, release8 and genuine8. */
public final class ImageAttachmentPasteJava8Fixture {
    private static final ArrayList<String> VECTORS = new ArrayList<String>();
    private static final ImageReference REF = new ImageReference(repeat("a", 64), "image/png", 3, 2, 75);
    private static final ImageReference OTHER = new ImageReference(repeat("b", 64), "image/png", 4, 2, 80);
    private ImageAttachmentPasteJava8Fixture() {}
    private static String repeat(String text, int n) { StringBuilder b = new StringBuilder(); for (int i=0;i<n;i++) b.append(text); return b.toString(); }
    private static void fact(String name, Object value) { VECTORS.add(name + "=" + value); }
    private static void check(boolean pass, String name) { if (!pass) throw new AssertionError(name); fact(name, true); }
    private interface Action { void run() throws Exception; }
    private static void fails(Class<? extends Throwable> type, String name, Action action) {
        try { action.run(); } catch (Throwable e) { if (!type.isInstance(e)) throw new AssertionError(name, e); fact(name, e.getClass().getName()); return; }
        throw new AssertionError(name + " did not reject");
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void value(Object instance, boolean reconstructEqual, String... expectedNames) throws Exception {
        Class<?> owner = instance.getClass();
        String name = owner.getSimpleName();
        ArrayList<String> names = new ArrayList<String>();
        ArrayList<String> types = new ArrayList<String>();
        ArrayList<Object> args = new ArrayList<Object>();
        Class<?>[] raw = new Class<?>[expectedNames.length];
        if (ValueSchemas.supports(owner)) {
            ValueSchema schema = ValueSchemas.of(owner);
            for (Object c : schema.components()) {
                ValueSchema.Component component = (ValueSchema.Component)c;
                names.add(component.name()); types.add(component.genericType().getTypeName()); args.add(component.read(instance));
                check(Modifier.isPrivate(component.fieldMetadata().getModifiers()) && Modifier.isFinal(component.fieldMetadata().getModifiers()), name + ".field." + component.name());
            }
            check(Modifier.isPublic(owner.getAnnotation(dev.openallay.value.ValueType.class).value().getConstructor().getModifiers()), name+".provider");
        } else {
            List<RecordMetadata.Component> components = RecordMetadata.components(owner);
            for (int i=0;i<components.size();i++) {
                RecordMetadata.Component component = components.get(i);
                names.add(component.name()); types.add(component.genericType().getTypeName());
                java.lang.reflect.Method accessor = component.accessorMetadata(); accessor.setAccessible(true); args.add(accessor.invoke(instance));
                raw[i] = component.rawType();
                Field field = owner.getDeclaredField(component.name());
                check(Modifier.isPrivate(field.getModifiers()) && Modifier.isFinal(field.getModifiers()), name+".field."+component.name());
            }
            // Same asserted public provider contract fact has no provider in the original record.
            check(RecordMetadata.isRecord(owner), name+".provider");
        }
        check(names.equals(Arrays.asList(expectedNames)), name+".components"); fact(name+".genericTypes", types);
        Object rebuilt;
        if (ValueSchemas.supports(owner)) rebuilt = ValueSchemas.of((Class)owner).construct(args.toArray());
        else { Constructor<?> constructor = owner.getDeclaredConstructor(raw); constructor.setAccessible(true); rebuilt = constructor.newInstance(args.toArray()); }
        check(instance.equals(rebuilt) == reconstructEqual, name+".reconstructionEquality");
        check(Modifier.isFinal(owner.getModifiers()), name+".final");
        int folded = 0;
        for (String component : expectedNames) {
            Field field = owner.getDeclaredField(component); field.setAccessible(true); Object part = field.get(instance);
            int hash = part == null ? 0 : part.hashCode();
            // Primitive reflection boxes implement the precise record primitive hash formulas.
            folded = 31 * folded + hash;
        }
        check(instance.hashCode() == folded, name+".rawFieldZeroSeed31Hash");
        StringBuilder text = new StringBuilder(name).append('[');
        for (int i=0;i<expectedNames.length;i++) {
            Field field = owner.getDeclaredField(expectedNames[i]); field.setAccessible(true);
            if(i>0)text.append(", ");text.append(expectedNames[i]).append('=').append(field.get(instance));
        }
        text.append(']');check(instance.toString().equals(text.toString()),name+".rawFieldToString");
        if(ValueSchemas.supports(owner)) {
            boolean rejected=false;try{ValueSchemas.of((Class)owner).construct(new Object[0]);}catch(IllegalArgumentException expected){rejected=true;}check(rejected,name+".schemaArity");
        } else {
            // The original record constructor has exactly the asserted canonical arity.
            check(owner.getDeclaredConstructor(raw).getParameterTypes().length==expectedNames.length,name+".schemaArity");
        }
        check(instance.equals(instance) && !instance.equals(null) && !instance.equals(new Object()),name+".equalityBoundary");
        // Scope and arrays have VM-local identities. Assert exact in-VM formula, never emit them.
    }
    private static BufferedImage bitmap() { BufferedImage b = new BufferedImage(3,2,BufferedImage.TYPE_INT_ARGB); b.setRGB(2,1,0xff123456); return b; }
    private static void values() throws Exception {
        BufferedImage image = bitmap();
        value(ImageClipboard.Read.image(image), true, "status", "image");
        check(Modifier.isPublic(ImageClipboard.Read.class.getModifiers()) && Modifier.isPublic(ImageClipboard.Read.class.getConstructor(ImageClipboard.Status.class, BufferedImage.class).getModifiers()), "Read.implicitInterfacePublicABI");
        fails(NullPointerException.class,"Read.nullStatus",()->new ImageClipboard.Read(null,null));
        fails(IllegalArgumentException.class,"Read.imageWithoutPixels",()->new ImageClipboard.Read(ImageClipboard.Status.IMAGE,null));
        fails(IllegalArgumentException.class,"Read.emptyWithPixels",()->new ImageClipboard.Read(ImageClipboard.Status.EMPTY,image));
        byte[] png = {1,2,3}; int[] argb = {0xff123456,0xff654321};
        ClipboardImageEncoder.Preview preview = new ClipboardImageEncoder.Preview(2,1,argb);
        ClipboardImageEncoder.Encoded encoded = new ClipboardImageEncoder.Encoded(png,3,2,preview);
        value(preview,false,"width","height","argb"); value(encoded,false,"png","width","height","preview");
        argb[0]=0; png[0]=0; check(preview.argb()[0]==0xff123456 && encoded.png()[0]==1,"buffers.constructorClone");
        preview.argb()[0]=0; encoded.png()[0]=0; check(preview.argb()[0]==0xff123456 && encoded.png()[0]==1,"buffers.accessorClone");
        check(!preview.equals(new ClipboardImageEncoder.Preview(2,1,preview.argb())),"Preview.arrayIdentityEquality");
        check(!encoded.equals(new ClipboardImageEncoder.Encoded(encoded.png(),3,2,preview)),"Encoded.arrayIdentityEquality");
        fails(IllegalArgumentException.class,"Preview.dimensions",()->new ClipboardImageEncoder.Preview(2,2,new int[3]));
        fails(NullPointerException.class,"Preview.null",()->new ClipboardImageEncoder.Preview(1,1,null));
        fails(NullPointerException.class,"Encoded.pngValidationFirst",()->new ClipboardImageEncoder.Encoded(null,1,1,null));
        fails(NullPointerException.class,"Encoded.preview",()->new ClipboardImageEncoder.Encoded(new byte[0],1,1,null));
        UUID id = UUID.fromString("10000000-0000-0000-0000-000000000001");
        value(new ComposerImageDraft.Attachment(id,REF,preview),true,"id","reference","preview");
        ArrayList<UUID> ids = new ArrayList<UUID>();ids.add(id);
        ComposerImageDraft.Submission submission = new ComposerImageDraft.Submission("one",2,ids);
        value(submission,true,"session","generation","ids"); ids.clear(); check(submission.ids().size()==1,"Submission.defensiveList");
        fails(UnsupportedOperationException.class,"Submission.immutableList",()->submission.ids().clear());
        fails(NullPointerException.class,"Submission.nullElement",()->new ComposerImageDraft.Submission("one",0,Arrays.asList((UUID)null)));
        Class<?> scope = Class.forName("dev.openallay.client.gui.ComposerImageDraft$Scope");
        check(Modifier.isPrivate(scope.getModifiers()) && Modifier.isStatic(scope.getModifiers()),"Scope.privateStaticABI");
        Constructor<?> constructor = scope.getDeclaredConstructor(String.class,long.class,long.class);
        check(Modifier.isPrivate(constructor.getModifiers()),"Scope.privateConstructorABI");
        // Fixture-only constructor invocation, never private-field writes or production allocation bypass.
        constructor.setAccessible(true); value(constructor.newInstance("one",2L,3L),true,"session","generation","lifetime");
        ComposerImageDraft.Submission json = EngineJson.create().fromJson(EngineJson.create().toJson(submission),ComposerImageDraft.Submission.class);
        check(json.equals(submission),"Submission.jsonPublicConstructor");
    }
    private static final class Queue implements Executor {
        final ArrayDeque<Runnable> tasks = new ArrayDeque<Runnable>();
        public void execute(Runnable task) { tasks.add(task); }
        void run() { while(!tasks.isEmpty())tasks.remove().run(); }
    }
    private static final class Clip implements ImageClipboard {
        int captures,reads,closes; Read next=Read.image(bitmap()); boolean captureThrows,readThrows;
        public ImageClipboard capture() { captures++;if(captureThrows)throw new IllegalStateException("synthetic");return this; }
        public Read read() { reads++;if(readThrows)throw new IllegalStateException("synthetic");return next; }
        public void close(){closes++;}
    }
    private static void composer() throws Exception {
        Clip clip=new Clip();Queue worker=new Queue(),client=new Queue();
        ArrayList<CompletableFuture<ToolResult<ImageReference>>> pending=new ArrayList<CompletableFuture<ToolResult<ImageReference>>>();
        ArrayList<ComposerImageDraft.Notice> notices=new ArrayList<ComposerImageDraft.Notice>();ArrayList<ImageReference> discarded=new ArrayList<ImageReference>();
        ComposerImageDraft draft=new ComposerImageDraft(clip,worker,client::execute,bytes->{check(bytes.length>8,"composer.actualPngImport"+pending.size());CompletableFuture<ToolResult<ImageReference>> f=new CompletableFuture<ToolResult<ImageReference>>();pending.add(f);return f;},notices::add,discarded::add);
        draft.paste();check(clip.captures==0,"composer.detachedNoClipboard");draft.attach("one");
        check(clip.reads==0,"composer.attachNoClipboard");draft.paste();check(draft.pending()&&clip.reads==0&&clip.captures==1,"composer.explicitPasteCaptureBeforeWorker");
        draft.selectSession("two");worker.run();check(clip.reads==1&&clip.closes==1,"composer.workerClose");client.run();
        pending.get(0).complete(new ToolResult.Success<ImageReference>(REF));client.run();
        check(draft.empty()&&draft.attachments("one").get(0).reference()==REF,"composer.originalSessionCompletion");check("one".equals(draft.changedSession()),"composer.changedSession");
        draft.selectSession("one");ComposerImageDraft.Submission submission=draft.captureSubmission();draft.restore(Arrays.asList(REF,OTHER));
        check(!draft.accepted(submission)&&draft.references().size()==2,"composer.generationRejectsOldSubmission");
        submission=draft.captureSubmission();draft.paste();check(draft.accepted(submission)&&draft.pending()&&draft.attachments().size()==1,"composer.acceptedLeavesLaterAddition");
        worker.run();client.run();draft.clear();pending.get(1).complete(new ToolResult.Success<ImageReference>(OTHER));client.run();
        check(draft.empty()&&discarded.equals(Arrays.asList(OTHER)),"composer.clearDiscardsLateImport");
        draft.paste();worker.run();client.run();draft.detach();pending.get(2).complete(new ToolResult.Success<ImageReference>(REF));client.run();
        check(draft.empty()&&discarded.equals(Arrays.asList(OTHER,REF)),"composer.detachDiscardsLateImport");
        draft.attach("one");draft.paste();worker.run();client.run();pending.get(3).complete(new ToolResult.Failure<ImageReference>("synthetic","synthetic failure"));client.run();check(draft.empty()&&notices.get(notices.size()-1)==ComposerImageDraft.Notice.IMPORT_FAILED,"composer.importFailure");
        clip.next=ImageClipboard.Read.empty();draft.paste();worker.run();client.run();check(draft.empty()&&notices.get(notices.size()-1)==ComposerImageDraft.Notice.NONE,"composer.emptyClipboard");
        clip.next=ImageClipboard.Read.unavailable();draft.paste();worker.run();client.run();check(draft.empty()&&notices.get(notices.size()-1)==ComposerImageDraft.Notice.CLIPBOARD_UNAVAILABLE,"composer.unavailableClipboard");
        clip.readThrows=true;draft.paste();worker.run();client.run();check(draft.empty()&&clip.closes==7,"composer.readFailureCloses");
        clip.captureThrows=true;draft.paste();check(draft.empty()&&clip.reads==7,"composer.captureFailureNoWorker");
        Clip rejected=new Clip();ComposerImageDraft rejectedDraft=new ComposerImageDraft(rejected,task->{throw new java.util.concurrent.RejectedExecutionException("synthetic");},Runnable::run,bytes->CompletableFuture.completedFuture(new ToolResult.Success<ImageReference>(REF)),n->{});
        rejectedDraft.attach("one");rejectedDraft.paste();check(rejected.closes==1&&rejected.reads==0&&rejectedDraft.empty(),"composer.rejectedWorkerCloses");
        fails(UnsupportedOperationException.class,"composer.attachmentsImmutable",()->draft.attachments().clear());
        draft.restore(Arrays.asList(REF,REF,OTHER));check(draft.retainedReferences().equals(Arrays.asList(REF,OTHER)),"composer.retainedDistinctIdentityPreserved");
        ComposerImageDraft.Attachment attachment=draft.attachments().get(0);ClipboardImageEncoder.Preview preview=new ClipboardImageEncoder.Preview(1,1,new int[1]);draft.preview(attachment.id(),preview);check(draft.attachments().get(0).preview()==preview,"composer.previewReplacement");draft.remove(attachment.id());check(draft.attachments().size()==2,"composer.removeOneOccurrence");
    }
    private static void images() throws Exception {
        BufferedImage source=new BufferedImage(123,61,BufferedImage.TYPE_INT_ARGB);source.setRGB(122,60,0xff123456);
        ClipboardImageEncoder.Encoded encoded=ClipboardImageEncoder.encode(source);BufferedImage decoded=ClipboardImageEncoder.decode(encoded.png());
        check(decoded.getWidth()==123&&decoded.getHeight()==61&&decoded.getRGB(122,60)==0xff123456,"encoding.fullPixelsNotThumbnail");
        check(encoded.preview().width()==40&&encoded.preview().height()==20&&encoded.preview().argb().length==800,"encoding.previewScale");
        AtomicInteger transfers=new AtomicInteger();Transferable text=new Transferable(){public DataFlavor[] getTransferDataFlavors(){return new DataFlavor[]{DataFlavor.stringFlavor};}public boolean isDataFlavorSupported(DataFlavor f){return f.equals(DataFlavor.stringFlavor);}public Object getTransferData(DataFlavor f){transfers.incrementAndGet();throw new AssertionError("ordinary text");}};
        check(new AwtImageClipboard(()->text).read().status()==ImageClipboard.Status.EMPTY&&transfers.get()==0,"clipboard.neverReadOrdinaryText");
        Path temporary=Files.createTempDirectory("openallay-synthetic-image-");UUID actor=UUID.fromString("10000000-0000-0000-0000-000000000001");
        try {
            FileImageAttachmentStore store=new FileImageAttachmentStore(temporary.resolve("managed"));ImageReference ref=store.importImage(actor,"synthetic-owner",encoded.png());
            check(ref.width()==123&&ref.height()==61&&ref.byteSize()==encoded.png().length,"storage.actualMetadata");
            check(Arrays.equals(store.read(actor,ref),encoded.png()),"storage.actualVerifiedBytes");
            check(new FileImageAttachmentStore(temporary.resolve("managed")).collect(actor)==0,"storage.reopenedDurableOwner");
            fails(java.io.IOException.class,"storage.actorIsolation",()->store.read(UUID.fromString("20000000-0000-0000-0000-000000000001"),ref));
            fails(IllegalArgumentException.class,"storage.unicodeBlankOwner",()->store.retain(actor,"\u2003",Collections.singletonList(ref)));
            store.release(actor,"synthetic-owner");check(store.collect(actor)==1,"storage.releaseCollect");
        } finally {
            // Only this fresh synthetic directory is removed; no repository/profile paths.
            try(java.util.stream.Stream<Path> paths=Files.walk(temporary)){for(Path path:dev.openallay.util.Java8Collections.toList(paths.sorted(java.util.Comparator.reverseOrder())))Files.delete(path);}
        }
    }
    public static String[] vectors() throws Exception { VECTORS.clear();values();images();composer();return VECTORS.toArray(new String[0]); }
    public static void main(String[] args) throws Exception {for(String vector:vectors())System.out.println(vector);}
}
