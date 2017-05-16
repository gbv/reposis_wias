
$(document).ready(function() {

// spam protection for mails
  $('span.madress').each(function(i) {
      var text = $(this).text();
      var address = text.replace(" [at] ", "@");
      $(this).after('<a href="mailto:'+address+'">'+ address +'</a>')
      $(this).remove();
  });


  $("#submit_publication .btn-default").on("click", function(e){
      e.preventDefault();
      var selectedGenre = $( "#genre option:selected" ).val();

      switch(selectedGenre) {
        case "preprint":
          $('#submit_publication').append('<input type="hidden" name="relatedItemId" value="wias_mods_00000012" />');
          $('#submit_publication').append('<input type="hidden" name="relatedItemType" value="series" />');
          $('#submit_publication').submit();
          break;
        case "report":
          $('#submit_publication').append('<input type="hidden" name="relatedItemId" value="wias_mods_00000021" />');
          $('#submit_publication').append('<input type="hidden" name="relatedItemType" value="series" />');
          $('#submit_publication').submit();
          break;
        case "technical_report":
          $('#submit_publication').append('<input type="hidden" name="relatedItemId" value="wias_mods_00000035" />');
          $('#submit_publication').append('<input type="hidden" name="relatedItemType" value="series" />');
          $('#submit_publication').submit();
          break;
        default:
          $('#submit_publication').submit();
      }

  });

});